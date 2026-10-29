package com.artbid.certificate.controller;

import com.artbid.certificate.dto.CertificateResponse;
import com.artbid.certificate.dto.CertificateVerifyResponse;
import com.artbid.certificate.service.CertificateService;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
public class CertificateController {

	private final CertificateService certificateService;

	// 보증서 발급·재발급 (작품 위탁자, 관리자)
	@PostMapping("/api/artworks/{artworkId}/certificates")
	@ResponseStatus(HttpStatus.CREATED)
	public CertificateResponse issue(@PathVariable Long artworkId, Principal principal, HttpServletRequest request) {
		return CertificateResponse.from(certificateService.issue(artworkId, memberId(principal), role(request)));
	}

	// 작품의 현재 보증서 (공개)
	@GetMapping("/api/artworks/{artworkId}/certificate")
	public CertificateResponse getActive(@PathVariable Long artworkId) {
		return CertificateResponse.from(certificateService.getActive(artworkId));
	}

	// 보증서 진위 확인 (공개). 결과는 항상 200으로 주고 valid/result로 구분한다
	@GetMapping("/api/certificates/{serialNumber}/verify")
	public CertificateVerifyResponse verify(@PathVariable String serialNumber,
			@RequestParam(required = false) String signature) {
		return certificateService.verify(serialNumber, signature);
	}

	static MemberRole role(HttpServletRequest request) {
		Object role = request.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE);
		return role instanceof MemberRole memberRole ? memberRole : null;
	}

	// Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다. 로그인하지 않았으면 null → 서비스가 401
	static Long memberId(Principal principal) {
		if (principal == null) {
			return null;
		}
		try {
			return Long.parseLong(principal.getName());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
