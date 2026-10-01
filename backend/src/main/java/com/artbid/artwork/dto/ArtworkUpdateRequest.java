package com.artbid.artwork.dto;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import jakarta.validation.constraints.*;

/**
 * 작품 부분 수정 요청 (PATCH). 보내지 않은(null) 필드는 기존 값을 유지한다.
 * 상태(status)는 여기서 바꾸지 않는다 — 심사 로직(#36)에서 처리.
 */
public record ArtworkUpdateRequest(
		Long artistId,
		@Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "제목은 공백일 수 없습니다") String title,
		@Size(max = 2000) String description,
		ArtworkCategory category,
		@Size(max = 100) String medium,
		@Positive Double widthCm,
		@Positive Double heightCm,
		@Positive Double depthCm,
		@Min(1000) @Max(9999) Integer productionYear,
		@Positive Long startPrice,
		@Positive Long estimatedPrice,
		@Size(max = 500) String certificateUrl,
		@Size(max = 500) String imageUrl) {

	public Artwork.Changes toChanges() {
		return new Artwork.Changes(artistId, title, description, category, medium, widthCm, heightCm, depthCm,
				productionYear, startPrice, estimatedPrice, certificateUrl, imageUrl);
	}
}
