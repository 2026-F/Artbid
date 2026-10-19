package com.artbid.artwork.exception;

import com.artbid.artwork.domain.ArtworkStatus;

public class ArtworkNotEditableException extends RuntimeException {

	public ArtworkNotEditableException(Long artworkId, ArtworkStatus status) {
		super("%s 상태의 작품은 수정할 수 없습니다 (심사 대기·반려 상태에서만 수정 가능): %d".formatted(status, artworkId));
	}
}
