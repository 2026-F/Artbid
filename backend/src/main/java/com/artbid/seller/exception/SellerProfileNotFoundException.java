package com.artbid.seller.exception;

public class SellerProfileNotFoundException extends RuntimeException {
    public SellerProfileNotFoundException(Long memberId) {
        super("판매자 프로필을 찾을 수 없습니다: memberId=" + memberId);
    }
}