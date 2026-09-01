package com.artbid.member.controller;

import com.artbid.member.domain.Member;
import com.artbid.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/{id}")
	public Member getMember(@PathVariable Long id) {
		return memberService.getMember(id);
	}
}
