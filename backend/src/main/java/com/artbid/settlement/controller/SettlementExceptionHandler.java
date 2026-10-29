package com.artbid.settlement.controller;

import com.artbid.settlement.exception.SettlementAccessDeniedException;
import com.artbid.settlement.exception.SettlementAuthRequiredException;
import com.artbid.settlement.exception.SettlementNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 정산 조회 API 전용 예외 처리 (SellerProfileExceptionHandler와 같은 방식).
 */
@Order(0)
@RestControllerAdvice(assignableTypes = SettlementController.class)
public class SettlementExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(SettlementNotFoundException.class)
	public ResponseEntity<ErrorResponse> notFound(SettlementNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("SETTLEMENT_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(SettlementAccessDeniedException.class)
	public ResponseEntity<ErrorResponse> forbidden(SettlementAccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("SETTLEMENT_FORBIDDEN", e.getMessage()));
	}

	// ?status=WRONG 처럼 값 형식이 틀린 경우
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> invalidParameter(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", e.getName() + " 값을 확인해주세요."));
	}

	@ExceptionHandler(SettlementAuthRequiredException.class)
	public ResponseEntity<ErrorResponse> authRequired(SettlementAuthRequiredException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("SETTLEMENT_AUTH_REQUIRED", e.getMessage()));
	}
}
