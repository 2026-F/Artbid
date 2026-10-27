package com.artbid.seller.exception;

public class SellerProfileAlreadyExistsException extends RuntimeException {
    public SellerProfileAlreadyExistsException(Long memberId) {
        super("이미 판매자 프로필이 있습니다: memberId=" + memberId);
    }
}