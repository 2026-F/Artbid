package com.artbid.common.exception;

import java.time.Duration;

/** 회원이 짧은 시간에 입찰을 너무 많이 보냈을 때. 429와 Retry-After로 돌려준다. */
public class BidRateLimitExceededException extends RuntimeException {

	private final Duration retryAfter;

	public BidRateLimitExceededException(Duration retryAfter) {
		super("입찰 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}
}
