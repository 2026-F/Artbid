package com.artbid.settlement.dto;

import com.artbid.settlement.domain.FeePolicy;

/** GET /api/fee-policy. 입찰 화면의 "낙찰 시 예상 결제 금액" 계산에 쓴다. */
public record FeePolicyResponse(double buyerPremiumRate, long shippingFee) {

	public static FeePolicyResponse current() {
		return new FeePolicyResponse(FeePolicy.BUYER_PREMIUM_RATE, FeePolicy.SHIPPING_FEE);
	}
}
