package com.artbid.common.exception;

public class BidTemporarilyUnavailableException extends RuntimeException {
    public BidTemporarilyUnavailableException() {
        super("지금 입찰이 몰려 있어요. 잠시 후 다시 시도해주세요.");
    }
}