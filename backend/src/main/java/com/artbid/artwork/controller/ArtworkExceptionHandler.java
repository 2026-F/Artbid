package com.artbid.artwork.controller;

import com.artbid.artist.exception.ArtistNotFoundException;
import com.artbid.artwork.exception.ArtworkNotEditableException;
import com.artbid.artwork.exception.ArtworkNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 작품 API 전용 예외 처리 (ArtistExceptionHandler·PaymentExceptionHandler와 같은 방식).
 */
@Order(0)
@RestControllerAdvice(assignableTypes = ArtworkController.class)
public class ArtworkExceptionHandler {

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(ArtworkNotFoundException.class)
	public ResponseEntity<ErrorResponse> artworkNotFound(ArtworkNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("ARTWORK_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(ArtistNotFoundException.class)
	public ResponseEntity<ErrorResponse> artistNotFound(ArtistNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("ARTIST_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(ArtworkNotEditableException.class)
	public ResponseEntity<ErrorResponse> notEditable(ArtworkNotEditableException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("ARTWORK_NOT_EDITABLE", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> invalidField(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.findFirst()
				.orElse("요청 형식을 확인해주세요.");
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ErrorResponse> unreadable(Exception e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "요청 형식을 확인해주세요."));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
	}
}
