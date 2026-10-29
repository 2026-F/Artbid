package com.artbid.artist.dto;

import com.artbid.artist.domain.Artist;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 작가 목록 응답. Spring의 Page 객체를 그대로 직렬화하지 않고 필요한 값만 내려준다.
 */
public record ArtistPageResponse(List<ArtistResponse> content, int page, int size, long totalElements,
		int totalPages) {

	public static ArtistPageResponse from(Page<Artist> page) {
		return new ArtistPageResponse(page.map(ArtistResponse::from).getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
