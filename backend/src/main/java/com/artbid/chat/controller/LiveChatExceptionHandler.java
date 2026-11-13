package com.artbid.chat.controller;

import com.artbid.chat.exception.ChatAuthRequiredException;
import com.artbid.chat.exception.ChatNotAvailableException;
import com.artbid.chat.exception.ChatRateLimitExceededException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 라이브 채팅 API 전용 예외 처리. StreamingExceptionHandler와 같은 {code, message} 형식을 쓴다.
 * 공통 GlobalExceptionHandler는 입력 검증 실패를 500으로 돌려주므로 여기서 400으로 잡는다.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = LiveChatController.class)
public class LiveChatExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(ChatAuthRequiredException.class)
	public ResponseEntity<ErrorResponse> authRequired(ChatAuthRequiredException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("CHAT_AUTH_REQUIRED", e.getMessage()));
	}

	@ExceptionHandler(ChatNotAvailableException.class)
	public ResponseEntity<ErrorResponse> notAvailable(ChatNotAvailableException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("CHAT_NOT_AVAILABLE", e.getMessage()));
	}

	@ExceptionHandler(ChatRateLimitExceededException.class)
	public ResponseEntity<ErrorResponse> rateLimited(ChatRateLimitExceededException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("CHAT_RATE_LIMITED", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> invalidMessage(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("메시지 형식이 올바르지 않습니다.");
		return ResponseEntity.badRequest().body(new ErrorResponse("CHAT_INVALID_MESSAGE", message));
	}

	/** 본문이 없거나 JSON이 아닌 요청 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("CHAT_INVALID_MESSAGE", "메시지 형식이 올바르지 않습니다."));
	}
}
