package com.artbid.settlement.dto;

import com.artbid.settlement.domain.PaymentStatus;
import com.artbid.settlement.domain.Settlement;

import java.time.LocalDateTime;

/**
 * 정산(낙찰자 청구) 응답. 엔티티를 그대로 내보내던 기존 응답과 같은 필드에 expiredAt만 추가했다.
 * paymentStatus가 FAILED이고 expiredAt이 있으면 결제 기한 만료다.
 */
public record SettlementResponse(
		Long id,
		Long auctionId,
		Long winnerId,
		Long finalPrice,
		Long premiumFee,
		Long shippingFee,
		Long totalAmount,
		LocalDateTime paymentDeadlineAt,
		PaymentStatus paymentStatus,
		LocalDateTime expiredAt) {

	public static SettlementResponse from(Settlement settlement) {
		return new SettlementResponse(
				settlement.getId(),
				settlement.getAuctionId(),
				settlement.getWinnerId(),
				settlement.getFinalPrice(),
				settlement.getPremiumFee(),
				settlement.getShippingFee(),
				settlement.getTotalAmount(),
				settlement.getPaymentDeadlineAt(),
				settlement.getPaymentStatus(),
				settlement.getExpiredAt());
	}
}
