package com.artbid.settlement.dto;

import com.artbid.settlement.domain.SellerPayout;
import com.artbid.settlement.domain.SellerPayoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SellerPayoutResponse(
		Long id,
		Long settlementId,
		Long auctionId,
		Long artworkId,
		Long finalPrice,
		BigDecimal commissionRate,
		Long commissionAmount,
		Long payoutAmount,
		SellerPayoutStatus status,
		LocalDateTime createdAt,
		LocalDateTime paidAt) {

	public static SellerPayoutResponse from(SellerPayout payout) {
		return new SellerPayoutResponse(
				payout.getId(),
				payout.getSettlementId(),
				payout.getAuctionId(),
				payout.getArtworkId(),
				payout.getFinalPrice(),
				payout.getCommissionRate(),
				payout.getCommissionAmount(),
				payout.getPayoutAmount(),
				payout.getStatus(),
				payout.getCreatedAt(),
				payout.getPaidAt());
	}
}
