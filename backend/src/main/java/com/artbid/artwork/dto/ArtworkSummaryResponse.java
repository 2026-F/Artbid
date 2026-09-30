package com.artbid.artwork.dto;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;

/**
 * 작품 목록의 한 항목. 기존 GET /api/artworks 응답(엔티티)에서 프론트가 쓰던
 * id, title, imageUrl, startPrice, estimatedPrice, status 필드명은 그대로 유지했다.
 */
public record ArtworkSummaryResponse(Long id, String title, Long artistId, String artistName,
		ArtworkCategory category, String imageUrl, Long startPrice, Long estimatedPrice, ArtworkStatus status) {

	public static ArtworkSummaryResponse of(Artwork artwork, String artistName) {
		return new ArtworkSummaryResponse(artwork.getId(), artwork.getTitle(), artwork.getArtistId(), artistName,
				artwork.getCategory(), artwork.getImageUrl(), artwork.getStartPrice(), artwork.getEstimatedPrice(),
				artwork.getStatus());
	}
}
