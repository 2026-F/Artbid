package com.artbid.liveshow.exception;

/** 지금 상태에서는 할 수 없는 요청(409). 예: 현재 작품 입찰 중에 다음 작품 열기 */
public class LiveShowConflictException extends RuntimeException {
	public LiveShowConflictException(String message) {
		super(message);
	}
}
