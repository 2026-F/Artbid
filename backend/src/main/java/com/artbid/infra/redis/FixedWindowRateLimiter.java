package com.artbid.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 고정 윈도우 횟수 카운터. 키의 첫 요청부터 window 동안 요청 수를 센다.
 * 증가와 만료시간 설정을 Lua 스크립트 하나로 처리해 만료 없는 키가 남지 않게 한다.
 * 제한 수치와 초과 시 예외는 쓰는 쪽에서 정한다.
 */
@Component
@RequiredArgsConstructor
public class FixedWindowRateLimiter {

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> fixedWindowCounterScript;

	/** 이번 요청을 포함해 현재 윈도우에서 센 횟수를 돌려준다. */
	public long increment(String key, Duration window) {
		Long count = redisTemplate.execute(fixedWindowCounterScript, List.of(key), String.valueOf(window.toMillis()));
		if (count == null) {
			throw new IllegalStateException("횟수 제한 카운터가 값을 돌려주지 않았습니다. key=" + key);
		}
		return count;
	}
}
