package com.artbid.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class BidLuaExecutor {

	private final StringRedisTemplate redisTemplate;

	// TODO: "현재가 조회 + 새 입찰가 비교 + 갱신 + 마감 30초 연장"을 원자적으로 처리하는
	// compare-and-set + 안티 스나이핑 Lua 스크립트로 교체
	private static final String COMPARE_AND_SET_SCRIPT =
			"return redis.call('GET', KEYS[1])";

	public Long tryBid(Long auctionId, Long bidderId, Long price) {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(COMPARE_AND_SET_SCRIPT, Long.class);
		return redisTemplate.execute(script, Collections.singletonList("auction:" + auctionId + ":price"));
	}
}
