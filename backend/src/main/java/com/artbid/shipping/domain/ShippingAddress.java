package com.artbid.shipping.domain;

/**
 * 낙찰자가 입력하는 배송지. 개인정보이므로 로그, 예외 메시지, toString에 값을 남기지 않는다.
 */
public record ShippingAddress(
		String recipientName,
		String recipientPhone,
		String zipCode,
		String address,
		String addressDetail,
		String requestMemo) {

	@Override
	public String toString() {
		return "ShippingAddress[masked]";
	}
}
