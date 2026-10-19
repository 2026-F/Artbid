package com.artbid.streaming.exception;

public class StageTokenRateLimitExceededException extends RuntimeException {

	public StageTokenRateLimitExceededException(String clientIp) {
		super("시청 토큰 발급 횟수를 초과했습니다. 잠시 후 다시 시도해주세요: ip=" + clientIp);
	}
}
