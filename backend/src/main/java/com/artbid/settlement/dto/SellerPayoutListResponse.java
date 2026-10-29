package com.artbid.settlement.dto;

import com.artbid.settlement.domain.SellerPayout;
import com.artbid.settlement.domain.SellerPayoutStatus;

import java.util.List;

/**
 * 위탁자 지급 내역과 합계.
 * readyAmount는 지급 대기 합계, paidAmount는 지급 완료 합계다.
 */
public record SellerPayoutListResponse(
		long readyAmount,
		long paidAmount,
		List<SellerPayoutResponse> payouts) {

	public static SellerPayoutListResponse from(List<SellerPayout> payouts) {
		return new SellerPayoutListResponse(
				sum(payouts, SellerPayoutStatus.READY),
				sum(payouts, SellerPayoutStatus.PAID),
				payouts.stream().map(SellerPayoutResponse::from).toList());
	}

	private static long sum(List<SellerPayout> payouts, SellerPayoutStatus status) {
		return payouts.stream()
				.filter(payout -> payout.getStatus() == status)
				.mapToLong(SellerPayout::getPayoutAmount)
				.sum();
	}
}
