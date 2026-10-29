package com.artbid.payment.dto;

import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentResponse(Long id, Long settlementId, Long amount, PaymentMethod method,
		PaymentStatus status, String transactionId, String failureReason,
		LocalDateTime requestedAt, LocalDateTime paidAt) {
	public static PaymentResponse from(Payment payment) {
		return new PaymentResponse(payment.getId(), payment.getSettlementId(), payment.getAmount(),
				payment.getMethod(), payment.getStatus(), payment.getTransactionId(),
				payment.getFailureReason(), payment.getRequestedAt(), payment.getPaidAt());
	}
}
