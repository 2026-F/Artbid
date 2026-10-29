package com.artbid.artist.exception;

public class ArtistNotFoundException extends RuntimeException {

	public ArtistNotFoundException(Long artistId) {
		super("작가를 찾을 수 없습니다: " + artistId);
	}
}
