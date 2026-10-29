package com.artbid.shipping.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 배송 API 오류. 결제 API의 PaymentException과 같은 방식(상태 코드, 오류 코드, 메시지). 메시지에 개인정보를 넣지 않는다. */
@Getter
public class ShipmentException extends RuntimeException {
	private final HttpStatus status;
	private final String code;

	public ShipmentException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}
}
