package com.artbid.liveshow.exception;

public class LiveShowNotFoundException extends RuntimeException {
	public LiveShowNotFoundException(Long showId) {
		super("라이브를 찾을 수 없습니다: " + showId);
	}
}
