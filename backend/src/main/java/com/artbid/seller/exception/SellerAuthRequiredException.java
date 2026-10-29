package com.artbid.seller.exception;

public class SellerAuthRequiredException extends RuntimeException {
    public SellerAuthRequiredException() {
        super("로그인이 필요합니다.");
    }
}