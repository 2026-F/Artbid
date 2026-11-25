package com.artbid.auction.service;

import com.artbid.auction.config.BidRateLimitProperties;
import com.artbid.common.exception.BidRateLimitExceededException;
import com.artbid.infra.redis.SlidingWindowRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 회원별 입찰 횟수 제한. 경매와 상관없이 회원 한 명의 입찰 제출과 취소를 합쳐서 센다.
 * 마감 직전 몰림을 정확히 막으려고 고정 윈도우 대신 Sorted Set 슬라이딩 윈도우를 쓴다.
 *
 * Redis 장애 때는 제한 없이 통과시킨다(fail-open). 횟수 제한은 보조 장치라서
 * 이것 때문에 정상 입찰까지 막히면 안 된다. 이때도 nginx의 IP 기준 제한은 그대로 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidRateLimiter {

	static final String KEY_PREFIX = "bid:rate:";

	private final SlidingWindowRateLimiter rateLimiter;
	private final BidRateLimitProperties properties;

	public void check(Long memberId) {
		if (!properties.isRateLimitEnabled()) {
			return;
		}

		SlidingWindowRateLimiter.Decision decision;
		try {
			decision = rateLimiter.tryAcquire(KEY_PREFIX + memberId, properties.getRateLimit(),
					properties.getRateLimitWindow());
		} catch (DataAccessException e) {
			log.warn("입찰 횟수 제한을 확인하지 못해 통과시킴 (Redis 오류): {}", e.getMessage());
			return;
		}

		if (!decision.allowed()) {
			throw new BidRateLimitExceededException(decision.retryAfter());
		}
	}
}
