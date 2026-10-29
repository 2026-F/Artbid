package com.artbid.member.dto;

import com.artbid.member.domain.Member;
import com.artbid.member.domain.MemberRole;

public record MemberResponse(Long id, String email, String name, MemberRole role) {
        public static MemberResponse from(Member member){
            return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getRole());
        }
}
