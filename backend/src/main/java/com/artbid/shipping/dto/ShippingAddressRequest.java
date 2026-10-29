package com.artbid.shipping.dto;

import com.artbid.shipping.domain.ShippingAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
		@NotBlank @Size(max = 50) String recipientName,
		// 010-1234-5678, 01012345678, 02-123-4567 같은 국내 번호
		@NotBlank @Pattern(regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$", message = "연락처 형식을 확인해주세요.") String recipientPhone,
		@NotBlank @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리입니다.") String zipCode,
		@NotBlank @Size(max = 200) String address,
		@Size(max = 200) String addressDetail,
		@Size(max = 200) String requestMemo) {

	public ShippingAddress toAddress() {
		return new ShippingAddress(recipientName, recipientPhone, zipCode, address, addressDetail, requestMemo);
	}

	@Override
	public String toString() {
		return "ShippingAddressRequest[masked]";
	}
}
