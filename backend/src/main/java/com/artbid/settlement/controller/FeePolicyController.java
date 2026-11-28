package com.artbid.settlement.controller;

import com.artbid.settlement.dto.FeePolicyResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 수수료와 배송비 조회. 비로그인 시청자도 예상 금액을 볼 수 있게 누구나 호출한다. */
@RestController
@RequestMapping("/api/fee-policy")
public class FeePolicyController {

	@GetMapping
	public FeePolicyResponse get() {
		return FeePolicyResponse.current();
	}
}
