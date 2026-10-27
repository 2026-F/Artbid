package com.artbid.seller.controller;

import com.artbid.seller.exception.SellerAuthRequiredException;
import com.artbid.seller.exception.SellerProfileAlreadyExistsException;
import com.artbid.seller.exception.SellerProfileForbiddenException;
import com.artbid.seller.exception.SellerProfileNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 판매자 프로필 API 전용 예외 처리 (ArtworkExceptionHandler·MemberExceptionHandler와 같은 방식).
 */
@Order(0)
@RestControllerAdvice(assignableTypes = SellerProfileController.class)
public class SellerProfileExceptionHandler {

    public record ErrorResponse(String code, String message) {
    }

    @ExceptionHandler(SellerProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(SellerProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("SELLER_PROFILE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SellerProfileAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> alreadyExists(SellerProfileAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("SELLER_PROFILE_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(SellerProfileForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(SellerProfileForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("SELLER_PROFILE_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(SellerAuthRequiredException.class)
    public ResponseEntity<ErrorResponse> authRequired(SellerAuthRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("SELLER_AUTH_REQUIRED", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidField(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("요청 형식을 확인해주세요.");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
    }
}