package com.artbid.artwork.exception;

public class ArtworkNotFoundException extends RuntimeException {

	public ArtworkNotFoundException(Long artworkId) {
		super("작품을 찾을 수 없습니다: " + artworkId);
	}
}
