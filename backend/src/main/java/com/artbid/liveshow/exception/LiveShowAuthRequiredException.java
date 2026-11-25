package com.artbid.liveshow.exception;

public class LiveShowAuthRequiredException extends RuntimeException {
	public LiveShowAuthRequiredException() {
		super("로그인이 필요합니다.");
	}
}
