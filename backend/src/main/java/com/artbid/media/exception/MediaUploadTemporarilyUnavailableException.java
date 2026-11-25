package com.artbid.media.exception;

public class MediaUploadTemporarilyUnavailableException extends RuntimeException {

	public MediaUploadTemporarilyUnavailableException(Throwable cause) {
		super("다른 요청이 이 작품을 처리 중입니다. 잠시 후 업로드 완료를 다시 시도해 주세요.", cause);
	}
}
