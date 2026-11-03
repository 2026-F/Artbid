package com.artbid.common.exception;

// 입찰/입찰취소 처리에는 검증된 회원 ID가 반드시 필요한데 없을 때 던지는 예외.
// (StreamingAuthRequiredException과 같은 방식)
public class BidAuthRequiredException extends RuntimeException {
    public BidAuthRequiredException() {
        super("로그인이 필요합니다.");
    }
}
