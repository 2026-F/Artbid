package com.artbid.artwork.dto;

import com.artbid.artist.domain.Artist;
import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.MediaType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 작품 상세. 작가 요약 정보와 업로드된 미디어(사진·영상·3D) 목록을 함께 내려준다.
 */
public record ArtworkDetailResponse(Long id, Long consignorId, ArtistSummary artist, String title,
		String description, ArtworkCategory category, String medium, Double widthCm, Double heightCm,
		Double depthCm, Integer productionYear, Long startPrice, Long estimatedPrice, String certificateUrl,
		String imageUrl, ArtworkStatus status, List<MediaItem> media, LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public record ArtistSummary(Long id, String name, String profileImageUrl) {
	}

	public record MediaItem(Long id, MediaType mediaType, String url, String sourceUrl, Integer sortOrder) {
	}

	public static ArtworkDetailResponse of(Artwork artwork, Artist artist, List<ArtworkMedia> media) {
		ArtistSummary artistSummary = artist == null ? null
				: new ArtistSummary(artist.getId(), artist.getName(), artist.getProfileImageUrl());
		List<MediaItem> mediaItems = media.stream()
				.map(m -> new MediaItem(m.getId(), m.getMediaType(), m.getUrl(), m.getSourceUrl(), m.getSortOrder()))
				.toList();

		return new ArtworkDetailResponse(artwork.getId(), artwork.getConsignorId(), artistSummary, artwork.getTitle(),
				artwork.getDescription(), artwork.getCategory(), artwork.getMedium(), artwork.getWidthCm(),
				artwork.getHeightCm(), artwork.getDepthCm(), artwork.getProductionYear(), artwork.getStartPrice(),
				artwork.getEstimatedPrice(), artwork.getCertificateUrl(), artwork.getImageUrl(), artwork.getStatus(),
				mediaItems, artwork.getCreatedAt(), artwork.getUpdatedAt());
	}
}
