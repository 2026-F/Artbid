package com.artbid.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 운송장 등록 요청. 택배사는 아직 코드 목록이 정해지지 않아 이름 문자열로 받는다(예: CJ대한통운).
 */
public record TrackingRequest(
		@NotBlank @Size(max = 30) String carrier,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{8,30}$", message = "운송장 번호는 영문, 숫자, 하이픈 8~30자입니다.") String trackingNumber) {
}
