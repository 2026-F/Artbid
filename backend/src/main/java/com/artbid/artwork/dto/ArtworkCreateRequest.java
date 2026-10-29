package com.artbid.artwork.dto;

import com.artbid.artwork.domain.ArtworkCategory;
import jakarta.validation.constraints.*;

/**
 * 작품 등록 요청.
 * consignorId는 인증(#33)이 붙기 전까지 임시로 요청 본문으로 받는다 — 인증 연동 후 로그인 사용자로 대체.
 */
public record ArtworkCreateRequest(
		@NotNull Long consignorId,
		@NotNull Long artistId,
		@NotBlank @Size(max = 100) String title,
		@Size(max = 2000) String description,
		@NotNull ArtworkCategory category,
		@Size(max = 100) String medium,
		@Positive Double widthCm,
		@Positive Double heightCm,
		@Positive Double depthCm,
		@Min(1000) @Max(9999) Integer productionYear,
		@NotNull @Positive Long startPrice,
		@Positive Long estimatedPrice,
		@Size(max = 500) String certificateUrl,
		@Size(max = 500) String imageUrl) {
}
