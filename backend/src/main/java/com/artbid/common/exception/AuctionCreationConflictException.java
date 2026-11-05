package com.artbid.common.exception;

// 경매를 열 수 없는 작품 상태일 때 던진다. (승인 전 작품, 이미 진행 중이거나 예정된 경매가 있는 작품)
public class AuctionCreationConflictException extends RuntimeException {

    public AuctionCreationConflictException(String message) {
        super(message);
    }
}
