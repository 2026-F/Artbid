package com.artbid.member.exception;

public class MemberAccessDeniedException extends RuntimeException {

    public MemberAccessDeniedException(Long memberId) {
        super("본인 정보만 수정할 수 있습니다: " + memberId);
    }
}