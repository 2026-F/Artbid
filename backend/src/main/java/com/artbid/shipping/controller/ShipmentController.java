package com.artbid.shipping.controller;

import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.shipping.dto.ShipmentResponse;
import com.artbid.shipping.dto.ShippingAddressRequest;
import com.artbid.shipping.service.ShipmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** 낙찰 건(정산)별 배송 정보. 결제 API(/api/settlements/{id}/payments)와 같은 위치에 둔다. */
@RestController
@RequestMapping("/api/settlements/{settlementId}/shipment")
@RequiredArgsConstructor
public class ShipmentController {

	private final ShipmentService shipmentService;

	// 배송 정보 조회 (낙찰자, 해당 작품 위탁자, 관리자)
	@GetMapping
	public ShipmentResponse get(@PathVariable Long settlementId, Principal principal, HttpServletRequest request) {
		return ShipmentResponse.from(shipmentService.get(settlementId, memberId(principal), role(request)));
	}

	// 배송지 입력·수정 (낙찰자 본인, 발송 전까지)
	@PutMapping
	public ShipmentResponse saveAddress(@PathVariable Long settlementId,
			@Valid @RequestBody ShippingAddressRequest request, Principal principal) {
		return ShipmentResponse.from(shipmentService.saveAddress(settlementId, memberId(principal), request.toAddress()));
	}

	static MemberRole role(HttpServletRequest request) {
		Object role = request.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE);
		return role instanceof MemberRole memberRole ? memberRole : null;
	}

	// Authentication 연동 계약: Principal.name에는 검증된 Member ID를 사용한다. (다른 컨트롤러들과 동일한 컨벤션)
	// 로그인하지 않았으면 null을 넘기고, 서비스가 401로 거절한다.
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
