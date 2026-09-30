package com.artbid.artwork.dto;

import com.artbid.artwork.domain.Artwork;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public record ArtworkPageResponse(List<ArtworkSummaryResponse> content, int page, int size, long totalElements,
		int totalPages) {

	public static ArtworkPageResponse of(Page<Artwork> page, Map<Long, String> artistNames) {
		List<ArtworkSummaryResponse> content = page.getContent().stream()
				.map(artwork -> ArtworkSummaryResponse.of(artwork, artistNames.get(artwork.getArtistId())))
				.toList();
		return new ArtworkPageResponse(content, page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}
}
