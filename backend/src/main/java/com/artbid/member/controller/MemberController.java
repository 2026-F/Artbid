package com.artbid.member.controller;

import com.artbid.member.domain.Member;
import com.artbid.member.dto.MemberResponse;
import com.artbid.member.dto.MemberUpdateRequest;
import com.artbid.member.exception.MemberAuthRequiredException;
import com.artbid.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/{id}")
	public MemberResponse getMember(@PathVariable Long id) {
		return MemberResponse.from(memberService.getMember(id));
	}

	// 본인 정보 수정(이름만). 로그인 토큰의 memberId == path의 id 여야 통과됨.
	@PatchMapping("/{id}")
	public MemberResponse updateMember(@PathVariable Long id, @Valid @RequestBody MemberUpdateRequest request,
									   Principal principal) {
		Member member = memberService.updateMember(id, memberId(principal), request);
		return MemberResponse.from(member);
	}

	// Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다. (Payment/Streaming 컨트롤러와 동일한 컨벤션)
	private Long memberId(Principal principal) {
		if (principal != null) {
			try {
				long id = Long.parseLong(principal.getName());
				if (id > 0) return id;
			} catch (NumberFormatException ignored) {
			}
		}
		throw new MemberAuthRequiredException();
	}
}