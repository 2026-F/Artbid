package com.artbid.infra.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 Redis에 붙어서 슬라이딩 윈도우 Lua 스크립트 동작을 확인한다.
 * 로컬에서는 docker compose의 redis, CI에서는 서비스 컨테이너를 쓴다. Redis가 없으면 건너뛴다.
 */
class SlidingWindowRateLimiterTest {

	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private SlidingWindowRateLimiter limiter;
	private String key;

	@BeforeEach
	void setUp() {
		String host = System.getenv().getOrDefault("SPRING_DATA_REDIS_HOST", "localhost");
		LettuceClientConfiguration client = LettuceClientConfiguration.builder()
				.commandTimeout(Duration.ofMillis(500))
				.build();
		connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, 6379), client);
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		assumeTrue(isRedisUp(), "Redis가 실행 중이 아니라 건너뜀");

		limiter = new SlidingWindowRateLimiter(redisTemplate,
				RedisScript.of(new ClassPathResource("scripts/sliding_window_limiter.lua"), List.class));
		key = "test:sliding:" + ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
	}

	@AfterEach
	void tearDown() {
		if (key != null && isRedisUp()) {
			redisTemplate.delete(key);
		}
		connectionFactory.destroy();
	}

	@Test
	void 한도까지는_허용하고_넘으면_남은_시간과_함께_거절한다() {
		Duration window = Duration.ofSeconds(10);

		for (int i = 1; i <= 3; i++) {
			SlidingWindowRateLimiter.Decision decision = limiter.tryAcquire(key, 3, window);
			assertThat(decision.allowed()).isTrue();
			assertThat(decision.count()).isEqualTo(i);
		}

		SlidingWindowRateLimiter.Decision rejected = limiter.tryAcquire(key, 3, window);
		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.count()).isEqualTo(3);
		assertThat(rejected.retryAfter()).isPositive().isLessThanOrEqualTo(window);
	}

	@Test
	void 거절된_요청은_기록하지_않고_키에는_만료가_걸린다() {
		Duration window = Duration.ofSeconds(10);
		limiter.tryAcquire(key, 1, window);
		limiter.tryAcquire(key, 1, window);
		limiter.tryAcquire(key, 1, window);

		assertThat(redisTemplate.opsForZSet().zCard(key)).isEqualTo(1);
		Long ttl = redisTemplate.getExpire(key);
		assertThat(ttl).isNotNull().isPositive();
	}

	@Test
	void 윈도우가_지나면_다시_허용한다() throws InterruptedException {
		Duration window = Duration.ofMillis(300);
		assertThat(limiter.tryAcquire(key, 1, window).allowed()).isTrue();
		assertThat(limiter.tryAcquire(key, 1, window).allowed()).isFalse();

		Thread.sleep(400);

		assertThat(limiter.tryAcquire(key, 1, window).allowed()).isTrue();
	}

	@Test
	void 동시에_몰려도_한도만큼만_허용한다() throws Exception {
		int limit = 5;
		int requests = 50;
		ExecutorService executor = Executors.newFixedThreadPool(16);
		try {
			List<Callable<Boolean>> tasks = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				tasks.add(() -> limiter.tryAcquire(key, limit, Duration.ofSeconds(10)).allowed());
			}
			long allowed = 0;
			for (Future<Boolean> future : executor.invokeAll(tasks)) {
				if (future.get()) {
					allowed++;
				}
			}
			assertThat(allowed).isEqualTo(limit);
		} finally {
			executor.shutdownNow();
		}
	}

	private boolean isRedisUp() {
		try {
			return "PONG".equals(redisTemplate.execute((RedisCallback<String>) c -> c.ping()));
		} catch (RuntimeException e) {
			return false;
		}
	}
}
