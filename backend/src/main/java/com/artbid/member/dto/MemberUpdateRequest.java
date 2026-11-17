package com.artbid.member.dto;

import com.artbid.member.domain.Member;
import jakarta.validation.constraints.Size;

// 보낸 항목만 바꾼다. 빠진 항목(null)과 공백뿐인 값은 기존 값을 유지한다.
public record MemberUpdateRequest(
        @Size(max = 50) String name,
        @Size(max = Member.MAX_NICKNAME_LENGTH) String nickname
) {
}
