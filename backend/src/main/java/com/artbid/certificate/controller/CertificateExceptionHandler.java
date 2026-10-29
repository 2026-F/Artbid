package com.artbid.certificate.controller;

import com.artbid.certificate.exception.CertificateException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 보증서 API 전용 예외 처리 (PaymentExceptionHandler와 같은 방식). */
@Order(0)
@RestControllerAdvice(assignableTypes = CertificateController.class)
public class CertificateExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(CertificateException.class)
	public ResponseEntity<ErrorResponse> certificate(CertificateException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> invalid(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", e.getName() + " 값을 확인해주세요."));
	}
}
