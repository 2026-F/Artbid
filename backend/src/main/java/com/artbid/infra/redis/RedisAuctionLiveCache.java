package com.artbid.infra.redis;

import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveCache;
import com.artbid.auction.live.AuctionLiveState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 경매 실시간 상태를 Redis 해시(auction:{id}:live)에 저장한다.
 *
 * - 변경 저장은 Lua 스크립트로 "저장된 변경보다 새로울 때만 덮어쓰기"를 한 번에 처리한다.
 *   커밋 후 처리는 요청 스레드마다 따로 돌기 때문에 늦게 끝난 이전 입찰이 최신 현재가를 덮어쓰는 것을 막는다.
 * - DB에서 읽은 스냅샷은 키가 없을 때만 저장한다. 스냅샷을 읽는 사이 입찰이 커밋돼 캐시가 먼저 갱신됐다면
 *   오래된 스냅샷으로 덮어쓰지 않는다. 반대로 스냅샷이 먼저 들어가 있으면 이후 변경이 항상 덮어쓴다.
 * - Redis 장애는 로그만 남기고 삼킨다. 조회는 빈 값을 돌려줘 DB로 대체되게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAuctionLiveCache implements AuctionLiveCache {

	static final String SOURCE_CHANGE = "CHANGE";
	static final String SOURCE_SNAPSHOT = "SNAPSHOT";

	// 마감 후에도 결과 화면 조회를 위해 하루 더 보관. 마감이 이미 지난 경매도 최소 1시간은 보관한다.
	private static final Duration KEEP_AFTER_END = Duration.ofDays(1);
	private static final Duration MIN_TTL = Duration.ofHours(1);

	// ARGV: version, currentPrice, minBidUnit, auctionEndAt, status, ttlMillis
	private static final DefaultRedisScript<Long> SAVE_CHANGE = new DefaultRedisScript<>("""
			if redis.call('HGET', KEYS[1], 'source') == 'CHANGE' then
			  local saved = tonumber(redis.call('HGET', KEYS[1], 'version'))
			  if saved and saved > tonumber(ARGV[1]) then
			    return 0
			  end
			end
			redis.call('HSET', KEYS[1], 'version', ARGV[1], 'currentPrice', ARGV[2], 'minBidUnit', ARGV[3],
			  'auctionEndAt', ARGV[4], 'status', ARGV[5], 'source', 'CHANGE')
			redis.call('PEXPIRE', KEYS[1], ARGV[6])
			return 1
			""", Long.class);

	private static final DefaultRedisScript<Long> SAVE_SNAPSHOT_IF_ABSENT = new DefaultRedisScript<>("""
			if redis.call('EXISTS', KEYS[1]) == 1 then
			  return 0
			end
			redis.call('HSET', KEYS[1], 'version', ARGV[1], 'currentPrice', ARGV[2], 'minBidUnit', ARGV[3],
			  'auctionEndAt', ARGV[4], 'status', ARGV[5], 'source', 'SNAPSHOT')
			redis.call('PEXPIRE', KEYS[1], ARGV[6])
			return 1
			""", Long.class);

	private final StringRedisTemplate redisTemplate;

	static String key(Long auctionId) {
		return "auction:" + auctionId + ":live";
	}

	@Override
	public Optional<AuctionLiveState> find(Long auctionId) {
		try {
			Map<Object, Object> fields = redisTemplate.opsForHash().entries(key(auctionId));
			if (fields.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(new AuctionLiveState(
					auctionId,
					Long.valueOf((String) fields.get("currentPrice")),
					Long.valueOf((String) fields.get("minBidUnit")),
					LocalDateTime.parse((String) fields.get("auctionEndAt")),
					AuctionStatus.valueOf((String) fields.get("status")),
					Long.parseLong((String) fields.get("version"))));
		} catch (RuntimeException e) {
			log.warn("[경매 실시간 캐시] 조회 실패, DB로 대체: auctionId={}, 원인={}", auctionId, e.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public void saveChange(AuctionLiveState state) {
		run(SAVE_CHANGE, state, "변경 저장");
	}

	@Override
	public void saveSnapshotIfAbsent(AuctionLiveState state) {
		run(SAVE_SNAPSHOT_IF_ABSENT, state, "스냅샷 저장");
	}

	private void run(DefaultRedisScript<Long> script, AuctionLiveState state, String action) {
		try {
			redisTemplate.execute(script, List.of(key(state.auctionId())),
					String.valueOf(state.version()),
					String.valueOf(state.currentPrice()),
					String.valueOf(state.minBidUnit()),
					state.auctionEndAt().toString(),
					state.status().name(),
					String.valueOf(ttl(state.auctionEndAt()).toMillis()));
		} catch (RuntimeException e) {
			log.warn("[경매 실시간 캐시] {} 실패, 다음 조회 때 DB에서 다시 채움: auctionId={}, 원인={}",
					action, state.auctionId(), e.getMessage());
		}
	}

	private static Duration ttl(LocalDateTime auctionEndAt) {
		Duration ttl = Duration.between(LocalDateTime.now(), auctionEndAt).plus(KEEP_AFTER_END);
		return ttl.compareTo(MIN_TTL) < 0 ? MIN_TTL : ttl;
	}
}
