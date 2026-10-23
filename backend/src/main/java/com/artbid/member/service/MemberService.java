package com.artbid.member.service;

import com.artbid.member.domain.Member;
import com.artbid.member.dto.MemberUpdateRequest;
import com.artbid.member.exception.MemberAccessDeniedException;
import com.artbid.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;

	public Member getMember(Long id) {
		return memberRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + id));
	}

	@Transactional
	public Member updateMember(Long id, Long requesterId, MemberUpdateRequest request) {
		requireSelf(id, requesterId);
		Member member = getMember(id);
		member.updateName(request.name());
		return member;
	}

	private void requireSelf(Long targetId, Long requesterId) {
		if (!targetId.equals(requesterId)) {
			throw new MemberAccessDeniedException(targetId);
		}
	}
}