package com.artbid.common.exception;

// 어드민이 아닌 사용자가 경매 상태를 직접 바꾸려고 할 때 던지는 예외.
public class AuctionForbiddenException extends RuntimeException {

    public AuctionForbiddenException() {
        super("관리자만 할 수 있는 작업입니다.");
    }
}
