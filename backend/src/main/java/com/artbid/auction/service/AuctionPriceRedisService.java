package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 입찰 판정을 Redis 안에서 끝내는 캐시.
 *
 * 예전에는 Redis에 "현재가" 하나만 두고, 상태·마감 검사를 위해 입찰마다 DB에서 경매를 읽었다(findById).
 * 그러면 결국 질 입찰(가격이 낮거나 마감된 경매)도 매번 DB 커넥션을 빌려 쓰게 된다.
 * 1,000명이 한 경매에 몰리면 대부분이 지는 입찰이라, 그 조회들이 커넥션 풀과 DB CPU를 차지했다.
 *
 * 그래서 판정에 필요한 값(현재가, 최소 단위, 상태, 마감 시각)을 Redis 해시 하나에 같이 두고
 * Lua 스크립트(bid.lua)가 한 번에 판정한다. 이제 DB를 거치는 건
 *  - 이긴 입찰(WIN)의 조건부 UPDATE + INSERT
 *  - 캐시가 비어 있을 때 한 번의 조회(prime)
 * 뿐이다.
 *
 * 키: auction:{id}:bid_state (해시)
 *   price   현재가
 *   unit    최소 입찰 단위
 *   status  경매 상태 (ONGOING, EXTENDED, CLOSED ...)
 *   end_ms  마감 시각 (epoch 밀리초)
 */
@Component
@RequiredArgsConstructor
public class AuctionPriceRedisService {

	// 이 시간 동안 입찰이 없으면 키가 사라지고, 다음 입찰이 DB에서 다시 채운다.
	// 이긴 입찰이 올 때마다 연장되므로 입찰이 몰리는 경매는 만료되지 않는다.
	// 짧게 잡은 이유: 혹시 캐시가 DB와 어긋나더라도(드문 경합) 최대 이 시간 안에 저절로 바로잡힌다.
	static final long TTL_SECONDS = 60;

	// LocalDateTime(시간대 없음) <-> epoch ms 변환 기준. 저장할 때와 읽을 때 같은 기준만 쓰면 된다.
	private static final ZoneId ZONE = ZoneId.systemDefault();

	private static final String MISS = "MISS";

	private final StringRedisTemplate redisTemplate;
	@SuppressWarnings("rawtypes")
	private final RedisScript<List> bidScript;
	private final RedisScript<Long> primeBidStateScript;

	public enum Outcome {
		WIN,          // 이겼다. Redis 현재가(필요하면 마감)도 이미 갱신됨 -> DB에 반영해야 한다
		TOO_LOW,      // 현재가 + 최소 단위보다 낮다
		NOT_BIDDABLE, // 진행 중(ONGOING/EXTENDED)이 아니다
		ENDED,        // 마감 시각이 지났다
		CACHE_MISS    // 캐시가 비어 있다 -> DB에서 읽어 prime() 후 다시 시도
	}

	/**
	 * 판정 결과. WIN이면 갱신된 뒤의 값, 그 외에는 판정 당시 Redis에 있던 값이다.
	 * CACHE_MISS면 outcome 외에는 전부 null/false다.
	 */
	public record BidDecision(
			Outcome outcome,
			Long currentPrice,
			Long minBidUnit,
			AuctionStatus status,
			LocalDateTime auctionEndAt,
			boolean extended) {

		static BidDecision cacheMiss() {
			return new BidDecision(Outcome.CACHE_MISS, null, null, null, null, false);
		}
	}

	/** 이 입찰이 이기는지 Redis 안에서 판정하고, 이기면 그 자리에서 현재가·마감을 갱신한다. */
	@SuppressWarnings("unchecked")
	public BidDecision tryBid(Long auctionId, Long price, LocalDateTime now) {
		List<Object> reply = (List<Object>) redisTemplate.execute(
				bidScript,
				List.of(bidStateKey(auctionId)),
				String.valueOf(price),
				String.valueOf(toEpochMilli(now)),
				String.valueOf(Auction.ADD_WINDOW_SECONDS),
				String.valueOf(Auction.ADD_EXTEND_SECONDS),
				String.valueOf(TTL_SECONDS));

		if (reply == null || reply.isEmpty()) {
			throw new IllegalStateException("Redis 입찰 스크립트 결과가 비어 있습니다. Redis 연결을 확인하세요.");
		}
		String code = String.valueOf(reply.get(0));
		if (MISS.equals(code)) {
			return BidDecision.cacheMiss();
		}
		return new BidDecision(
				Outcome.valueOf(code),
				Long.parseLong(String.valueOf(reply.get(1))),
				Long.parseLong(String.valueOf(reply.get(2))),
				AuctionStatus.valueOf(String.valueOf(reply.get(3))),
				toLocalDateTime(Long.parseLong(String.valueOf(reply.get(4)))),
				"1".equals(String.valueOf(reply.get(5))));
	}

	/**
	 * DB에서 읽은 경매로 캐시를 채운다. 이미 채워져 있으면 덮어쓰지 않는다.
	 * (동시에 캐시 미스가 난 다른 요청이 먼저 채웠고, 그 뒤 Redis에서 이긴 입찰이 있을 수 있기 때문)
	 */
	public void prime(Auction auction) {
		redisTemplate.execute(
				primeBidStateScript,
				List.of(bidStateKey(auction.getId())),
				String.valueOf(auction.getCurrentPrice()),
				String.valueOf(auction.getMinBidUnit()),
				auction.getStatus().name(),
				String.valueOf(toEpochMilli(auction.getAuctionEndAt())),
				String.valueOf(TTL_SECONDS));
	}

	/**
	 * 캐시를 비운다. DB 값을 다시 읽어 덮어쓰지 않고 지우기만 하는 이유:
	 * 지우는 시점과 다시 쓰는 시점 사이에 더 높은 입찰이 Redis에서 이겼다면, 다시 쓴 옛 값이 그걸 덮어써 버린다.
	 * 지우기만 하면 다음 요청이 CACHE_MISS로 떨어지면서 그 시점의 DB 값으로 새로 채운다.
	 */
	public void invalidate(Long auctionId) {
		redisTemplate.delete(bidStateKey(auctionId));
	}

	static String bidStateKey(Long auctionId) {
		return "auction:" + auctionId + ":bid_state";
	}

	static long toEpochMilli(LocalDateTime time) {
		return time.atZone(ZONE).toInstant().toEpochMilli();
	}

	static LocalDateTime toLocalDateTime(long epochMilli) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZONE);
	}
}
