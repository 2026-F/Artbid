package com.artbid.artist.controller;

import com.artbid.artist.exception.ArtistNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 작가 API 전용 예외 처리. 공통 GlobalExceptionHandler는 검증 실패를 500으로 내려서
 * PaymentExceptionHandler와 같은 방식으로 컨트롤러 범위를 한정해 둔다.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = ArtistController.class)
public class ArtistExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(ArtistNotFoundException.class)
	public ResponseEntity<ErrorResponse> notFound(ArtistNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("ARTIST_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> invalidField(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.findFirst()
				.orElse("요청 형식을 확인해주세요.");
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			IllegalArgumentException.class})
	public ResponseEntity<ErrorResponse> invalid(Exception e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "요청 형식을 확인해주세요."));
	}
}
