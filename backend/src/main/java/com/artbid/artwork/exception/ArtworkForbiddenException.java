package com.artbid.artwork.exception;

public class ArtworkForbiddenException extends RuntimeException {

    public ArtworkForbiddenException() {
        super("관리자만 할 수 있는 작업입니다.");
    }
}