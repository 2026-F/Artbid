package com.artbid.certificate.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 보증서 API 오류 (PaymentException과 같은 방식). */
@Getter
public class CertificateException extends RuntimeException {
	private final HttpStatus status;
	private final String code;

	public CertificateException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}
}
