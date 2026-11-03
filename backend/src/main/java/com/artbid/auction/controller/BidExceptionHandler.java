package com.artbid.auction.controller;

import com.artbid.common.exception.BidAuthRequiredException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// BidController 전용 예외 처리 (PaymentExceptionHandler와 같은 방식).
// price/bidderId 같은 필드가 비어서 들어오면 500(NPE)이 아니라 400으로 돌려주기 위해 추가했다.
@Order(0)
@RestControllerAdvice(assignableTypes = BidController.class)
public class BidExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(BidAuthRequiredException.class)
	public ResponseEntity<ErrorResponse> authRequired(BidAuthRequiredException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("AUTH_REQUIRED", e.getMessage()));
	}

	// @Valid 검증 실패(price 누락 등), JSON 파싱 실패, 경로/파라미터 타입 불일치를 전부 400으로 묶어서 처리.
	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ErrorResponse> invalid(Exception e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "요청 형식을 확인해주세요."));
	}
}
