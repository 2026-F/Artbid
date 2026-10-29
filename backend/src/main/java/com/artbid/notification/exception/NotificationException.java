package com.artbid.notification.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 알림 API 오류 (PaymentException과 같은 방식). */
@Getter
public class NotificationException extends RuntimeException {
	private final HttpStatus status;
	private final String code;

	public NotificationException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}
}
