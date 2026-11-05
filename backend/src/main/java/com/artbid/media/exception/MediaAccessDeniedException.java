package com.artbid.media.exception;

// 작품의 위탁자나 관리자가 아닌 회원이 업로드하거나, 다른 작품 경로의 objectKey를 등록하려 할 때 던진다.
public class MediaAccessDeniedException extends RuntimeException {

    public MediaAccessDeniedException(String message) {
        super(message);
    }
}
