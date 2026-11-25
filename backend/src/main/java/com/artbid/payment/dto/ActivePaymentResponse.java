package com.artbid.payment.dto;

import com.artbid.settlement.domain.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

public record ActivePaymentResponse(
		PaymentStatus settlementStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime expiredAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime paymentDeadlineAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime serverNow,
		@JsonInclude(JsonInclude.Include.ALWAYS) ActivePayment payment) {

	public record ActivePayment(Long id, com.artbid.payment.domain.PaymentStatus status) {}
}
