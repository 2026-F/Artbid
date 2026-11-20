package com.artbid.media.exception;

public class MediaTypeConflictException extends RuntimeException {
	public MediaTypeConflictException() {
		super("이미 등록된 객체의 미디어 유형과 다릅니다.");
	}
}
