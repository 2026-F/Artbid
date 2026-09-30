package com.artbid.artwork.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ArtworkStatus {
	PENDING_REVIEW, PREVIEW, IN_AUCTION, SOLD, REJECTED;

	// 목록 조회에서 status를 지정하지 않았을 때 보여줄 상태 (심사 대기·반려 작품은 공개 목록에서 제외)
	public static final Set<ArtworkStatus> PUBLIC = EnumSet.of(PREVIEW, IN_AUCTION, SOLD);

	public boolean isEditable() {
		return this == PENDING_REVIEW || this == REJECTED;
	}
}
