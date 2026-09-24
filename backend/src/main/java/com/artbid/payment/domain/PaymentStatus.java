package com.artbid.payment.domain;

/** 개별 결제 시도의 상태. 정산 도메인의 결제 상태와 구분한다. */
public enum PaymentStatus {
	REQUESTED, PAID, FAILED
}
