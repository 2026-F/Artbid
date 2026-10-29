package com.artbid.infra.redis;

import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 Redis에 붙어서 Lua 스크립트 동작을 확인한다.
 * 로컬에서는 docker compose의 redis, CI에서는 서비스 컨테이너를 쓴다. Redis가 없으면 건너뛴다.
 */
class RedisAuctionLiveCacheTest {

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisAuctionLiveCache cache;
	private Long auctionId;

	@BeforeEach
	void setUp() {
		String host = System.getenv().getOrDefault("SPRING_DATA_REDIS_HOST", "localhost");
		connectionFactory = factory(host, 6379);
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		assumeTrue(isRedisUp(), "Redis가 실행 중이 아니라 건너뜀");

		cache = new RedisAuctionLiveCache(redisTemplate);
		auctionId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
	}

	@AfterEach
	void tearDown() {
		if (auctionId != null && isRedisUp()) {
			redisTemplate.delete(RedisAuctionLiveCache.key(auctionId));
		}
		connectionFactory.destroy();
	}

	private static LettuceConnectionFactory factory(String host, int port) {
		LettuceClientConfiguration client = LettuceClientConfiguration.builder()
				.commandTimeout(Duration.ofMillis(500))
				.build();
		LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), client);
		factory.afterPropertiesSet();
		factory.start();
		return factory;
	}

	private boolean isRedisUp() {
		try {
			return "PONG".equals(redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping()));
		} catch (RuntimeException e) {
			return false;
		}
	}

	private AuctionLiveState state(long price, long version) {
		return new AuctionLiveState(auctionId, price, 1_000L,
				LocalDateTime.of(2026, 10, 29, 18, 0), AuctionStatus.ONGOING, version);
	}

	@Test
	void 저장한_변경을_그대로_읽는다() {
		cache.saveChange(state(15_000L, 100L));

		assertThat(cache.find(auctionId)).contains(state(15_000L, 100L));
	}

	@Test
	void 값이_없으면_빈_값이다() {
		assertThat(cache.find(auctionId)).isEmpty();
	}

	@Test
	void 더_오래된_변경은_최신_변경을_덮어쓰지_않는다() {
		cache.saveChange(state(16_000L, 200L));
		cache.saveChange(state(15_000L, 100L)); // 늦게 도착한 이전 입찰

		assertThat(cache.find(auctionId).orElseThrow().currentPrice()).isEqualTo(16_000L);
	}

	@Test
	void 더_새로운_변경은_덮어쓴다() {
		cache.saveChange(state(15_000L, 100L));
		cache.saveChange(state(14_000L, 200L)); // 입찰 취소로 현재가가 내려간 경우

		assertThat(cache.find(auctionId).orElseThrow().currentPrice()).isEqualTo(14_000L);
	}

	@Test
	void 스냅샷은_이미_값이_있으면_덮어쓰지_않는다() {
		cache.saveChange(state(16_000L, 200L));
		cache.saveSnapshotIfAbsent(state(15_000L, AuctionLiveState.SNAPSHOT_VERSION));

		assertThat(cache.find(auctionId).orElseThrow().currentPrice()).isEqualTo(16_000L);
	}

	@Test
	void 변경은_먼저_들어간_스냅샷을_항상_덮어쓴다() {
		cache.saveSnapshotIfAbsent(state(15_000L, AuctionLiveState.SNAPSHOT_VERSION));
		cache.saveChange(state(16_000L, 1L));

		assertThat(cache.find(auctionId).orElseThrow().currentPrice()).isEqualTo(16_000L);
	}

	@Test
	void 저장하면_만료시간이_설정된다() {
		cache.saveChange(state(15_000L, 100L));

		Long ttlSeconds = redisTemplate.getExpire(RedisAuctionLiveCache.key(auctionId));
		assertThat(ttlSeconds).isPositive();
	}

	@Test
	void Redis에_연결할_수_없으면_예외_없이_빈_값을_돌려준다() {
		LettuceConnectionFactory downFactory = factory("localhost", 1); // 아무것도 없는 포트
		try {
			StringRedisTemplate downTemplate = new StringRedisTemplate(downFactory);
			downTemplate.afterPropertiesSet();
			RedisAuctionLiveCache downCache = new RedisAuctionLiveCache(downTemplate);

			assertThatCode(() -> downCache.saveChange(state(15_000L, 100L))).doesNotThrowAnyException();
			assertThatCode(() -> downCache.saveSnapshotIfAbsent(state(15_000L, 0L))).doesNotThrowAnyException();
			assertThat(downCache.find(auctionId)).isEmpty();
		} finally {
			downFactory.destroy();
		}
	}
}
