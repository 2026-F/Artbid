package com.artbid.common.exception;

import com.artbid.artwork.exception.ArtworkNotEditableException;
import com.artbid.artwork.exception.ArtworkForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // 추가: 입찰 검증 실패는 404가 아니라 409(Conflict)가 의미상 더 맞음
    @ExceptionHandler(InvalidBidException.class)
    public ResponseEntity<String> handleInvalidBid(InvalidBidException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

    @ExceptionHandler(BidTemporarilyUnavailableException.class)
    public ResponseEntity<String> handleBidTemporarilyUnavailable(BidTemporarilyUnavailableException e){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(ArtworkNotEditableException.class)
    public ResponseEntity<String> handleArtworkNotEditable(ArtworkNotEditableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    // 어드민 권한이 필요한 작업(작품 승인/반려, 경매 상태 변경)을 일반 사용자가 호출했을 때.
    // 이 핸들러가 없으면 기본 Exception 핸들러로 떨어져서 403 대신 500이 나간다.
    @ExceptionHandler({ArtworkForbiddenException.class, AuctionForbiddenException.class})
    public ResponseEntity<String> handleForbidden(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}