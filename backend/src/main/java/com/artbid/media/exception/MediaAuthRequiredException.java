package com.artbid.media.exception;

// 미디어 업로드에는 검증된 회원 ID가 필요한데 없을 때 던진다.
public class MediaAuthRequiredException extends RuntimeException {

    public MediaAuthRequiredException() {
        super("로그인이 필요합니다.");
    }
}
