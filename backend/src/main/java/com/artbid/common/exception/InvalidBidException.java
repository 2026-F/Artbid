package com.artbid.common.exception;

// 입찰 검증 실패시 던지는 예외
public class InvalidBidException extends  RuntimeException{
    public InvalidBidException(String message){
        super(message);
    }
}
