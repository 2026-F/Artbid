package com.artbid.notification.domain;

/**
 * 지금 실제로 만드는 것은 AUCTION_WON(정산 생성 시), PAYMENT_COMPLETED(결제 완료 시)다. 나머지는 후속 연결용이다.
 */
public enum NotificationType {
	AUCTION_WON,       // 낙찰 (경매 마감 이벤트로 정산을 만들 때 SettlementCreationService가 보냄)
	OUTBID,            // 내 입찰가보다 높은 입찰
	PAYMENT_COMPLETED, // 결제 완료
	PAYMENT_DEADLINE,  // 결제 기한 임박·만료
	SHIPMENT_SHIPPED,  // 발송 (운송장 등록)
	PAYOUT_READY,      // 위탁자 지급 대기
	SYSTEM             // 공지 등
}
