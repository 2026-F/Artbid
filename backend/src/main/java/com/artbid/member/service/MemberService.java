package com.artbid.member.service;

import com.artbid.member.domain.Member;
import com.artbid.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;

	public Member getMember(Long id) {
		return memberRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + id));
	}
}
