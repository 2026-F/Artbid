package com.artbid.settlement.domain;

/**
 * 위탁자 지급 상태.
 * PAID는 아직 바꾸는 기능이 없다(실제 송금 연동과 지급 시점은 정책 미정). 나중에 값을 추가하면
 * 기존 DB의 CHECK 제약 때문에 저장이 실패하므로 처음부터 함께 정의해 둔다.
 */
public enum SellerPayoutStatus {
	READY, // 지급 대기 (금액 확정, 송금 전)
	PAID   // 지급 완료
}
