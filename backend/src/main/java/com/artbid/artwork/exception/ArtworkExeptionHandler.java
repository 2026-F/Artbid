package com.artbid.artwork.exception;

import com.artbid.artwork.exception.ArtworkForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ExceptionHandler(ArtworkForbiddenException.class)
public ResponseEntity<ErrorResponse> forbidden(ArtworkForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("ARTWORK_FORBIDDEN", e.getMessage()));
}
