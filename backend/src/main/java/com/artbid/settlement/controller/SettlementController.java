package com.artbid.settlement.controller;

import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.settlement.dto.SettlementResponse;
import com.artbid.settlement.exception.SettlementAuthRequiredException;
import com.artbid.settlement.service.SettlementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

	private final SettlementService settlementService;

	@GetMapping("/{id}")
	public SettlementResponse getSettlement(@PathVariable Long id, Principal principal, HttpServletRequest request) {
		return SettlementResponse.from(settlementService.getSettlement(id, memberId(principal), role(request)));
	}

	@GetMapping(params = "auctionId")
	public SettlementResponse getSettlementByAuctionId(@RequestParam Long auctionId, Principal principal,
			HttpServletRequest request) {
		return SettlementResponse.from(
				settlementService.getSettlementByAuctionId(auctionId, memberId(principal), role(request)));
	}

	// Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다. (다른 컨트롤러들과 동일한 컨벤션)
	private Long memberId(Principal principal) {
		if (principal != null) {
			try {
				long id = Long.parseLong(principal.getName());
				if (id > 0) {
					return id;
				}
			} catch (NumberFormatException ignored) {
			}
		}
		throw new SettlementAuthRequiredException();
	}

	private MemberRole role(HttpServletRequest request) {
		Object role = request.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE);
		return role instanceof MemberRole memberRole ? memberRole : null;
	}
}
