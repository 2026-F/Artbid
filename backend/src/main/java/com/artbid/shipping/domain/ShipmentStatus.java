package com.artbid.shipping.domain;

/**
 * 배송 상태.
 * DELIVERED는 아직 바꾸는 기능이 없다(수령 확인은 후속 범위). 나중에 값을 추가하면 기존 DB의 CHECK 제약 때문에
 * 저장이 실패하므로 처음부터 함께 정의해 둔다. (정산 배치의 SellerPayoutStatus와 같은 이유)
 */
public enum ShipmentStatus {
	READY,     // 배송지 입력 완료, 발송 대기
	SHIPPED,   // 운송장 등록, 발송 완료
	DELIVERED  // 수령 완료 (후속)
}
