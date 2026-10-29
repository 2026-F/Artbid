package com.artbid.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** amount는 일부러 안 받음 — 서버가 DB에 저장된 금액만 신뢰해서 사용한다. */
public record TossConfirmRequest(@NotBlank String paymentKey, @NotBlank String orderId) {
}