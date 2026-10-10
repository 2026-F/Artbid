package com.artbid.streaming.exception;

public class StreamingAuthRequiredException extends RuntimeException {

	public StreamingAuthRequiredException() {
		super("로그인이 필요합니다.");
	}
}
