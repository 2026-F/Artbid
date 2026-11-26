package com.artbid.payment.dto;

import com.artbid.settlement.domain.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public record ActivePaymentResponse(
		PaymentStatus settlementStatus,
		LocalDateTime expiredAt,
		@JsonInclude(JsonInclude.Include.ALWAYS) ActivePayment payment) {

	public record ActivePayment(Long id, com.artbid.payment.domain.PaymentStatus status) {}
}
