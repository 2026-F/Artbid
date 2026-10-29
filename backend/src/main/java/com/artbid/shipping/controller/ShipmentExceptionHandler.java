package com.artbid.shipping.controller;

import com.artbid.shipping.exception.ShipmentException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 배송 API 전용 예외 처리 (PaymentExceptionHandler와 같은 방식). */
@Order(0)
@RestControllerAdvice(assignableTypes = ShipmentController.class)
public class ShipmentExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(ShipmentException.class)
	public ResponseEntity<ErrorResponse> shipment(ShipmentException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	// 어떤 필드가 틀렸는지만 알려준다. 입력값 자체(주소, 연락처)는 응답에 다시 넣지 않는다.
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> invalidField(MethodArgumentNotValidException e) {
		String field = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField())
				.findFirst()
				.orElse("요청");
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", field + " 값을 확인해주세요."));
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ErrorResponse> invalid(Exception e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "요청 형식을 확인해주세요."));
	}
}
