package com.artbid.liveshow.controller;

import com.artbid.liveshow.exception.LiveShowAccessDeniedException;
import com.artbid.liveshow.exception.LiveShowAuthRequiredException;
import com.artbid.liveshow.exception.LiveShowConflictException;
import com.artbid.liveshow.exception.LiveShowNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 라이브 편성 API 전용 예외 처리. 채팅, 방송과 같은 {code, message} 형식을 쓴다. */
@Order(0)
@RestControllerAdvice(assignableTypes = LiveShowController.class)
public class LiveShowExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(LiveShowAuthRequiredException.class)
	public ResponseEntity<ErrorResponse> authRequired(LiveShowAuthRequiredException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("LIVE_SHOW_AUTH_REQUIRED", e.getMessage()));
	}

	@ExceptionHandler(LiveShowAccessDeniedException.class)
	public ResponseEntity<ErrorResponse> accessDenied(LiveShowAccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("LIVE_SHOW_FORBIDDEN", e.getMessage()));
	}

	@ExceptionHandler(LiveShowNotFoundException.class)
	public ResponseEntity<ErrorResponse> notFound(LiveShowNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("LIVE_SHOW_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(LiveShowConflictException.class)
	public ResponseEntity<ErrorResponse> conflict(LiveShowConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("LIVE_SHOW_CONFLICT", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("LIVE_SHOW_INVALID_REQUEST", e.getMessage()));
	}

	/** 본문이 없거나 JSON이 아닌 요청, 날짜 형식 오류 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("LIVE_SHOW_INVALID_REQUEST", "요청 형식이 올바르지 않습니다."));
	}

	/** ?status=값이 상태 이름이 아닐 때 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("LIVE_SHOW_INVALID_REQUEST", e.getName() + " 값이 올바르지 않습니다."));
	}
}
