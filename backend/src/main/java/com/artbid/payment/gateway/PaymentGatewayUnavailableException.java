package com.artbid.payment.gateway;

/** Timeout 등 승인 여부를 알 수 없는 경우. 명확한 PG 거절과 구분한다. */
public class PaymentGatewayUnavailableException extends RuntimeException {
	public PaymentGatewayUnavailableException(String message) {
		super(message);
	}
}
