package com.artbid.common.exception;

// 경매 요청 값이 잘못됐을 때 던진다. (예: 재오픈 진행 시간이 범위를 벗어남)
public class InvalidAuctionRequestException extends RuntimeException {

    public InvalidAuctionRequestException(String message) {
        super(message);
    }
}
