package com.artbid.member.exception;

public class MemberAuthRequiredException extends RuntimeException {

    public MemberAuthRequiredException() {
        super("로그인이 필요합니다.");
    }
}