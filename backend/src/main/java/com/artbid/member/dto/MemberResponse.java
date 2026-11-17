package com.artbid.member.dto;

import com.artbid.member.domain.Member;
import com.artbid.member.domain.MemberRole;

// nickname은 아직 정하지 않은 회원이면 null이다.
public record MemberResponse(Long id, String email, String name, String nickname, MemberRole role) {
        public static MemberResponse from(Member member){
            return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getNickname(),
                    member.getRole());
        }
}
