package com.artbid.artwork.exception;

// 작품 등록, 수정에는 검증된 회원 ID가 필요한데 없을 때 던진다. (BidAuthRequiredException과 같은 방식)
public class ArtworkAuthRequiredException extends RuntimeException {

    public ArtworkAuthRequiredException() {
        super("로그인이 필요합니다.");
    }
}
