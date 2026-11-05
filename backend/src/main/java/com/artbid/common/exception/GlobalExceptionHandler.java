package com.artbid.common.exception;

import com.artbid.media.exception.MediaAccessDeniedException;
import com.artbid.media.exception.MediaAuthRequiredException;
import com.artbid.artwork.exception.ArtworkNotEditableException;
import com.artbid.artwork.exception.ArtworkForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    // 추가: 입찰 검증 실패는 404가 아니라 409(Conflict)가 의미상 더 맞음
    @ExceptionHandler(InvalidBidException.class)
    public ResponseEntity<String> handleInvalidBid(InvalidBidException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    // 위에서 구체적으로 처리하지 못한 예외는 전부 여기로 떨어진다.
    // 반드시 로그를 남겨야 한다 — 안 그러면 500이 나도 원인을 전혀 알 수 없다 (실제로 이것 때문에 한참 헤맸음).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("처리되지 않은 예외 발생: {}", e.getClass().getName(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

    @ExceptionHandler(BidTemporarilyUnavailableException.class)
    public ResponseEntity<String> handleBidTemporarilyUnavailable(BidTemporarilyUnavailableException e){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(MediaAuthRequiredException.class)
    public ResponseEntity<String> handleMediaAuthRequired(MediaAuthRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(MediaAccessDeniedException.class)
    public ResponseEntity<String> handleMediaAccessDenied(MediaAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(InvalidAuctionRequestException.class)
    public ResponseEntity<String> handleInvalidAuctionRequest(InvalidAuctionRequestException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(AuctionCreationConflictException.class)
    public ResponseEntity<String> handleAuctionCreationConflict(AuctionCreationConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
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