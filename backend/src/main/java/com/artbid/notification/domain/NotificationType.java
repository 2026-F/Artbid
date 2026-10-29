package com.artbid.notification.domain;

/**
 * 알림 종류. 화면은 이 값으로 아이콘이나 문구를 고를 수 있다.
 * 지금 실제로 만드는 것은 PAYMENT_COMPLETED뿐이다. 나머지는 후속 연결용이다.
 * 나중에 값을 추가하면 기존 DB의 CHECK 제약 때문에 저장이 실패하므로 예상되는 종류를 미리 정의해 둔다.
 */
public enum NotificationType {
	AUCTION_WON,       // 낙찰 (경매 마감, Kafka 작업과 함께 연결 예정)
	OUTBID,            // 내 입찰가보다 높은 입찰
	PAYMENT_COMPLETED, // 결제 완료
	PAYMENT_DEADLINE,  // 결제 기한 임박·만료
	SHIPMENT_SHIPPED,  // 발송 (운송장 등록)
	PAYOUT_READY,      // 위탁자 지급 대기
	SYSTEM             // 공지 등
}
