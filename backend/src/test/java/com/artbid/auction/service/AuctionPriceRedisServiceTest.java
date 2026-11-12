package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.service.AuctionPriceRedisService.BidDecision;
import com.artbid.auction.service.AuctionPriceRedisService.Outcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * bid.lua / prime_bid_state.lua를 진짜 Redis에 돌려서 판정 규칙이 Auction 도메인과 같은지 확인한다.
 * Lua 스크립트는 목(mock)으로는 검증할 수 없어서 실제 Redis가 필요하다.
 *
 * CI에는 redis 서비스 컨테이너가 떠 있다(SPRING_DATA_REDIS_HOST). 로컬에서 Redis가 없으면 건너뛴다.
 * 스프링 컨텍스트 없이 연결만 직접 만들어서 빠르게 돈다.
 */
class AuctionPriceRedisServiceTest {

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;

	private AuctionPriceRedisService service;
	private Long auctionId;

	// 스크립트에는 "지금 시각"을 인자로 넘기므로 실제 시계와 상관없이 고정값으로 테스트할 수 있다.
	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@BeforeAll
	static void connect() {
		String host = System.getenv().getOrDefault("SPRING_DATA_REDIS_HOST", "localhost");
		connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(host, 6379),
				LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(3)).build());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		boolean redisUp;
		try (var connection = connectionFactory.getConnection()) {
			redisUp = "PONG".equals(connection.ping());
		} catch (Exception e) {
			redisUp = false;
		}
		assumeTrue(redisUp, "Redis(" + host + ":6379)에 연결할 수 없어 건너뜀");

		redisTemplate = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	static void disconnect() {
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	@BeforeEach
	@SuppressWarnings("rawtypes")
	void setUp() {
		RedisScript<List> bidScript = RedisScript.of(new ClassPathResource("scripts/bid.lua"), List.class);
		RedisScript<Long> primeScript = RedisScript.of(new ClassPathResource("scripts/prime_bid_state.lua"), Long.class);
		service = new AuctionPriceRedisService(redisTemplate, bidScript, primeScript);
		// 다른 테스트·로컬 데이터와 키가 겹치지 않게 큰 임의 id를 쓴다
		auctionId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
	}

	@AfterEach
	void cleanUp() {
		if (redisTemplate != null) {
			redisTemplate.delete(AuctionPriceRedisService.bidStateKey(auctionId));
		}
	}

	private Auction auction(long currentPrice, AuctionStatus status, LocalDateTime endAt) {
		return Auction.builder()
				.id(auctionId)
				.currentPrice(currentPrice)
				.minBidUnit(1_000L)
				.status(status)
				.auctionEndAt(endAt)
				.build();
	}

	private String storedPrice() {
		return (String) redisTemplate.opsForHash().get(AuctionPriceRedisService.bidStateKey(auctionId), "price");
	}

	@Test
	void 캐시가_없으면_CACHE_MISS를_돌려준다() {
		assertThat(service.tryBid(auctionId, 20_000L, now).outcome()).isEqualTo(Outcome.CACHE_MISS);
	}

	@Test
	void 현재가_더하기_최소단위보다_낮으면_TOO_LOW이고_값은_그대로다() {
		service.prime(auction(10_000L, AuctionStatus.ONGOING, now.plusMinutes(10)));

		BidDecision decision = service.tryBid(auctionId, 10_999L, now);

		assertThat(decision.outcome()).isEqualTo(Outcome.TOO_LOW);
		assertThat(decision.currentPrice()).isEqualTo(10_000L);
		assertThat(decision.minBidUnit()).isEqualTo(1_000L);
		assertThat(storedPrice()).isEqualTo("10000");
	}

	@Test
	void 현재가_더하기_최소단위와_같으면_이기고_현재가가_바뀐다() {
		LocalDateTime endAt = now.plusMinutes(10);
		service.prime(auction(10_000L, AuctionStatus.ONGOING, endAt));

		BidDecision decision = service.tryBid(auctionId, 11_000L, now);

		assertThat(decision.outcome()).isEqualTo(Outcome.WIN);
		assertThat(decision.currentPrice()).isEqualTo(11_000L);
		assertThat(decision.status()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(decision.auctionEndAt()).isEqualTo(endAt);
		assertThat(decision.extended()).isFalse();
		assertThat(storedPrice()).isEqualTo("11000");

		// 방금 이긴 가격 기준으로 다음 판정을 한다
		assertThat(service.tryBid(auctionId, 11_500L, now).outcome()).isEqualTo(Outcome.TOO_LOW);
		assertThat(service.tryBid(auctionId, 12_000L, now).outcome()).isEqualTo(Outcome.WIN);
	}

	@Test
	void 마감까지_30초_이하로_남았을_때_이기면_30초_연장되고_EXTENDED가_된다() {
		LocalDateTime endAt = now.plusSeconds(30); // 경계값: 정확히 30초 남음 -> 연장 (Auction.applyBid와 같은 <=)
		service.prime(auction(10_000L, AuctionStatus.ONGOING, endAt));

		BidDecision decision = service.tryBid(auctionId, 11_000L, now);

		assertThat(decision.outcome()).isEqualTo(Outcome.WIN);
		assertThat(decision.extended()).isTrue();
		assertThat(decision.status()).isEqualTo(AuctionStatus.EXTENDED);
		assertThat(decision.auctionEndAt()).isEqualTo(endAt.plusSeconds(30));
	}

	@Test
	void 마감까지_31초_남았으면_연장하지_않는다() {
		LocalDateTime endAt = now.plusSeconds(31);
		service.prime(auction(10_000L, AuctionStatus.ONGOING, endAt));

		BidDecision decision = service.tryBid(auctionId, 11_000L, now);

		assertThat(decision.extended()).isFalse();
		assertThat(decision.status()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(decision.auctionEndAt()).isEqualTo(endAt);
	}

	@Test
	void 연장된_경매에_또_입찰하면_연장된_마감_기준으로_다시_연장된다() {
		LocalDateTime endAt = now.plusSeconds(10);
		service.prime(auction(10_000L, AuctionStatus.ONGOING, endAt));
		service.tryBid(auctionId, 11_000L, now); // 마감 = endAt + 30초 (40초 남음)

		BidDecision second = service.tryBid(auctionId, 12_000L, now.plusSeconds(15)); // 25초 남은 시점

		assertThat(second.extended()).isTrue();
		assertThat(second.auctionEndAt()).isEqualTo(endAt.plusSeconds(60));
	}

	@Test
	void 진행_중이_아닌_상태면_가격과_상관없이_NOT_BIDDABLE이다() {
		service.prime(auction(10_000L, AuctionStatus.CLOSED, now.plusMinutes(10)));

		BidDecision decision = service.tryBid(auctionId, 1_000_000L, now);

		assertThat(decision.outcome()).isEqualTo(Outcome.NOT_BIDDABLE);
		assertThat(decision.status()).isEqualTo(AuctionStatus.CLOSED);
		assertThat(storedPrice()).isEqualTo("10000");
	}

	@Test
	void 마감_시각이_지났으면_ENDED이다() {
		service.prime(auction(10_000L, AuctionStatus.ONGOING, now.minusSeconds(1)));

		BidDecision decision = service.tryBid(auctionId, 1_000_000L, now);

		assertThat(decision.outcome()).isEqualTo(Outcome.ENDED);
		assertThat(storedPrice()).isEqualTo("10000");
	}

	@Test
	void 마감_시각과_정확히_같은_순간의_입찰은_받는다() {
		// Auction.assertBiddable은 now.isAfter(end)일 때만 거절한다
		service.prime(auction(10_000L, AuctionStatus.ONGOING, now));

		assertThat(service.tryBid(auctionId, 11_000L, now).outcome()).isEqualTo(Outcome.WIN);
	}

	@Test
	void 이미_채워진_캐시는_prime이_덮어쓰지_않는다() {
		service.prime(auction(10_000L, AuctionStatus.ONGOING, now.plusMinutes(10)));
		service.tryBid(auctionId, 12_000L, now); // Redis에서 이김 (DB는 아직 10,000일 수 있음)

		// 동시에 캐시 미스를 겪었던 다른 요청이 DB의 옛 값(10,000)으로 채우려 해도
		service.prime(auction(10_000L, AuctionStatus.ONGOING, now.plusMinutes(10)));

		assertThat(storedPrice()).isEqualTo("12000");
		assertThat(service.tryBid(auctionId, 12_500L, now).currentPrice()).isEqualTo(12_000L);
	}

	@Test
	void invalidate하면_다시_CACHE_MISS가_된다() {
		service.prime(auction(10_000L, AuctionStatus.ONGOING, now.plusMinutes(10)));

		service.invalidate(auctionId);

		assertThat(service.tryBid(auctionId, 11_000L, now).outcome()).isEqualTo(Outcome.CACHE_MISS);
	}

	@Test
	void 키에는_만료시간이_걸린다() {
		service.prime(auction(10_000L, AuctionStatus.ONGOING, now.plusMinutes(10)));
		service.tryBid(auctionId, 11_000L, now);

		Long ttl = redisTemplate.getExpire(AuctionPriceRedisService.bidStateKey(auctionId));

		assertThat(ttl).isBetween(1L, AuctionPriceRedisService.TTL_SECONDS);
	}

	@Test
	void 큰_금액도_지수_표기_없이_정확히_돌려준다() {
		long big = 123_456_789_012L; // 1,234억
		service.prime(auction(big, AuctionStatus.ONGOING, now.plusMinutes(10)));

		BidDecision decision = service.tryBid(auctionId, big + 1_000L, now);

		assertThat(decision.outcome()).isEqualTo(Outcome.WIN);
		assertThat(decision.currentPrice()).isEqualTo(big + 1_000L);
		assertThat(storedPrice()).isEqualTo(String.valueOf(big + 1_000L));
	}

	@Test
	void 밀리초_단위까지_마감시각을_보존한다() {
		LocalDateTime endAt = now.plusMinutes(10).plusNanos(123_456_789);
		service.prime(auction(10_000L, AuctionStatus.ONGOING, endAt));

		BidDecision decision = service.tryBid(auctionId, 10_500L, now); // TOO_LOW라도 상태는 돌려준다

		assertThat(decision.auctionEndAt()).isEqualTo(endAt.truncatedTo(ChronoUnit.MILLIS));
	}
}
