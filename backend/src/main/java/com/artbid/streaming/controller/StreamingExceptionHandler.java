package com.artbid.streaming.controller;

import com.artbid.streaming.exception.LivestreamAlreadyLiveException;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * 라이브 방송 API 전용 예외 처리. 공통 GlobalExceptionHandler는 그대로 두고
 * ArtistExceptionHandler와 같은 방식으로 이 컨트롤러에만 적용한다.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = StreamingController.class)
public class StreamingExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(LivestreamNotFoundException.class)
	public ResponseEntity<ErrorResponse> notFound(LivestreamNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("LIVESTREAM_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(LivestreamAlreadyLiveException.class)
	public ResponseEntity<ErrorResponse> alreadyLive(LivestreamAlreadyLiveException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("LIVESTREAM_ALREADY_LIVE", e.getMessage()));
	}

	/** AWS 자격 증명 누락·권한 부족·할당량 초과 등. 내부 메시지는 숨기고 502로 알린다. */
	@ExceptionHandler(SdkException.class)
	public ResponseEntity<ErrorResponse> awsFailure(SdkException e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse("IVS_UNAVAILABLE", "라이브 방송 서버와 통신하지 못했습니다. 잠시 후 다시 시도해주세요."));
	}
}
