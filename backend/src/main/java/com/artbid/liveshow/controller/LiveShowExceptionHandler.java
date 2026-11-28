package com.artbid.liveshow.controller;

import com.artbid.chat.exception.ChatAuthRequiredException;
import com.artbid.chat.exception.ChatNicknameRequiredException;
import com.artbid.chat.exception.ChatRateLimitExceededException;
import com.artbid.liveshow.exception.LiveShowAccessDeniedException;
import com.artbid.streaming.exception.StageTokenRateLimitExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import software.amazon.awssdk.core.exception.SdkException;
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

	/** 채팅 본문 검증(@Valid ChatMessageRequest) 실패. 공통 핸들러는 500으로 돌려주므로 여기서 400으로 잡는다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("요청 형식이 올바르지 않습니다.");
		return ResponseEntity.badRequest().body(new ErrorResponse("LIVE_SHOW_INVALID_REQUEST", message));
	}

	/** 토큰이 유효하지만 회원이 없는 경우(탈퇴 등). 경매 채팅과 같이 로그인 필요로 본다. */
	@ExceptionHandler(ChatAuthRequiredException.class)
	public ResponseEntity<ErrorResponse> chatAuthRequired(ChatAuthRequiredException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse("LIVE_SHOW_AUTH_REQUIRED", e.getMessage()));
	}

	/** 닉네임을 정하면 다시 보낼 수 있어 경매 채팅과 같은 코드로 409를 돌려준다. */
	@ExceptionHandler(ChatNicknameRequiredException.class)
	public ResponseEntity<ErrorResponse> nicknameRequired(ChatNicknameRequiredException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("CHAT_NICKNAME_REQUIRED", e.getMessage()));
	}

	@ExceptionHandler(ChatRateLimitExceededException.class)
	public ResponseEntity<ErrorResponse> chatRateLimited(ChatRateLimitExceededException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("CHAT_RATE_LIMITED", e.getMessage()));
	}

	@ExceptionHandler(StageTokenRateLimitExceededException.class)
	public ResponseEntity<ErrorResponse> tokenRateLimited(StageTokenRateLimitExceededException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("STAGE_TOKEN_RATE_LIMITED", e.getMessage()));
	}

	/** AWS 자격 증명 누락, 권한 부족, 할당량 초과 등. 내부 메시지는 숨기고 502로 알린다(경매 방송 API와 같음). */
	@ExceptionHandler(SdkException.class)
	public ResponseEntity<ErrorResponse> awsFailure(SdkException e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse("IVS_UNAVAILABLE", "라이브 방송 서버와 통신하지 못했습니다. 잠시 후 다시 시도해주세요."));
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
