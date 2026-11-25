package com.artbid.infra.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis Sorted Set 슬라이딩 윈도우 횟수 제한. 최근 window 동안의 요청 시각을 모두 기록해서 센다.
 * 고정 윈도우(FixedWindowRateLimiter)는 윈도우 경계 앞뒤로 짧은 시간에 한도의 2배까지 통과할 수 있다.
 * 순간 몰림이 문제인 입찰에는 이 방식을 쓴다. 대신 요청마다 기록이 남아 메모리를 더 쓴다.
 * 정리, 세기, 추가는 Lua 스크립트(sliding_window_limiter.lua) 하나로 처리한다.
 * 제한 수치와 초과 시 예외는 쓰는 쪽에서 정한다.
 */
@Component
public class SlidingWindowRateLimiter {

	private final StringRedisTemplate redisTemplate;
	@SuppressWarnings("rawtypes")
	private final RedisScript<List> script;

	@SuppressWarnings("rawtypes")
	public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate,
			@Qualifier("slidingWindowLimiterScript") RedisScript<List> script) {
		this.redisTemplate = redisTemplate;
		this.script = script;
	}

	/** 이번 요청을 허용하면 기록에 남긴다. 거절한 요청은 기록하지 않는다. */
	public Decision tryAcquire(String key, int limit, Duration window) {
		List<?> result = redisTemplate.execute(script, List.of(key),
				String.valueOf(window.toMillis()), String.valueOf(limit), UUID.randomUUID().toString());
		if (result == null || result.size() < 3) {
			throw new IllegalStateException("횟수 제한 스크립트가 값을 돌려주지 않았습니다. key=" + key);
		}
		return new Decision(toLong(result.get(0)) == 1, toLong(result.get(1)),
				Duration.ofMillis(toLong(result.get(2))));
	}

	private static long toLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(String.valueOf(value));
	}

	/**
	 * @param allowed    이번 요청을 받았는지
	 * @param count      윈도우 안에 기록된 요청 수 (허용됐으면 이번 요청 포함)
	 * @param retryAfter 거절됐을 때 다음 요청이 가능해지기까지 남은 시간. 허용됐으면 0
	 */
	public record Decision(boolean allowed, long count, Duration retryAfter) {
	}
}
