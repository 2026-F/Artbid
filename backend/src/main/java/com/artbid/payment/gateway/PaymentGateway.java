package com.artbid.payment.gateway;

public interface PaymentGateway {
	Result pay(Long paymentId, Long amount);

	record Result(boolean approved, String transactionId, String failureReason) {
		public Result {
			if (approved && (transactionId == null || transactionId.isBlank() || transactionId.length() > 100)
					|| !approved && (failureReason == null || failureReason.isBlank() || failureReason.length() > 500)) {
				throw new IllegalArgumentException("PG 결과에 거래 ID 또는 실패 사유가 필요합니다.");
			}
		}
	}
}
