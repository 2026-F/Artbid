package com.artbid.member.controller;

import com.artbid.member.exception.MemberAccessDeniedException;
import com.artbid.member.exception.MemberAuthRequiredException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


//회원 API 전용 예외 처리

@Order(0)
@RestControllerAdvice(assignableTypes = MemberController.class)
public class MemberExceptionHandler {

    public record ErrorResponse(String code, String message) {
    }

    @ExceptionHandler(MemberAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(MemberAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("MEMBER_ACCESS_DENIED", e.getMessage()));
    }

    @ExceptionHandler(MemberAuthRequiredException.class)
    public ResponseEntity<ErrorResponse> authRequired(MemberAuthRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("MEMBER_AUTH_REQUIRED", e.getMessage()));
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