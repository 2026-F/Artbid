package com.artbid.settlement.domain;

/**
 * 낙찰자가 내는 수수료와 배송비. 정산 생성(SettlementCreationService)과 조회 API(GET /api/fee-policy)가 같은 값을 쓴다.
 * 값을 바꾸면 이미 만들어진 정산은 그대로이고, 새로 마감되는 경매부터 적용된다.
 */
public final class FeePolicy {

	/** 낙찰가 대비 구매 수수료율 */
	public static final double BUYER_PREMIUM_RATE = 0.10;

	/** 배송비(원). 지금은 작품과 관계없이 같다 */
	public static final long SHIPPING_FEE = 30_000L;

	private FeePolicy() {
	}

	/** 반올림 방식은 기존 정산 생성과 같다. FE 예상 금액 계산도 같은 방식을 쓴다. */
	public static long buyerPremiumOf(long finalPrice) {
		return Math.round(finalPrice * BUYER_PREMIUM_RATE);
	}
}
