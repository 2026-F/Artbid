package com.artbid.streaming.exception;

import com.artbid.streaming.domain.LivestreamStatus;

public class InvalidStreamTransitionException extends RuntimeException {

	public InvalidStreamTransitionException(LivestreamStatus from, LivestreamStatus to) {
		super("현재 상태(%s)에서는 %s로 바꿀 수 없습니다.".formatted(from, to));
	}
}
