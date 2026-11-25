package com.artbid.liveshow.exception;

/** 로그인했지만 권한이 없는 요청(403). 예: 진행자가 아닌 회원의 시작, 남의 작품 편성 */
public class LiveShowAccessDeniedException extends RuntimeException {
	public LiveShowAccessDeniedException(String message) {
		super(message);
	}
}
