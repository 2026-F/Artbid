package com.artbid.artwork.controller;

import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.artwork.dto.ArtworkCreateRequest;
import com.artbid.artwork.dto.ArtworkDetailResponse;
import com.artbid.artwork.dto.ArtworkPageResponse;
import com.artbid.artwork.dto.ArtworkUpdateRequest;
import com.artbid.artwork.repository.ArtworkSearchCondition;
import com.artbid.artwork.service.ArtworkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/artworks")
@RequiredArgsConstructor
public class ArtworkController {

	private static final int MAX_PAGE_SIZE = 100;

	private final ArtworkService artworkService;

	@PostMapping
	public ResponseEntity<ArtworkDetailResponse> register(@Valid @RequestBody ArtworkCreateRequest request) {
		ArtworkDetailResponse response = artworkService.register(request);
		return ResponseEntity.created(URI.create("/api/artworks/" + response.id())).body(response);
	}

	/**
	 * 작품 목록 (검색·필터·정렬·페이지).
	 * status를 지정하지 않으면 공개 상태(PREVIEW, IN_AUCTION, SOLD)만 조회한다.
	 * sort: latest(기본, 최신 등록순) | priceAsc | priceDesc (시작가 기준)
	 */
	@GetMapping
	public ArtworkPageResponse getArtworks(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long artistId,
			@RequestParam(required = false) ArtworkCategory category,
			@RequestParam(required = false) ArtworkStatus status,
			@RequestParam(required = false) Long minPrice,
			@RequestParam(required = false) Long maxPrice,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
			throw new IllegalArgumentException("minPrice는 maxPrice보다 클 수 없습니다");
		}

		Set<ArtworkStatus> statuses = status == null ? ArtworkStatus.PUBLIC : Set.of(status);
		ArtworkSearchCondition condition = new ArtworkSearchCondition(keyword, artistId, category, statuses, minPrice,
				maxPrice);
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				toSort(sort));

		return artworkService.getArtworks(condition, pageable);
	}

	@GetMapping("/{artworkId}")
	public ArtworkDetailResponse getArtwork(@PathVariable Long artworkId) {
		return artworkService.getArtwork(artworkId);
	}

	@PatchMapping("/{artworkId}")
	public ArtworkDetailResponse update(@PathVariable Long artworkId, @Valid @RequestBody ArtworkUpdateRequest request) {
		return artworkService.update(artworkId, request);
	}

	private Sort toSort(String sort) {
		return switch (sort) {
			case "latest" -> Sort.by(Sort.Direction.DESC, "id");
			case "priceAsc" -> Sort.by(Sort.Order.asc("startPrice"), Sort.Order.desc("id"));
			case "priceDesc" -> Sort.by(Sort.Order.desc("startPrice"), Sort.Order.desc("id"));
			default -> throw new IllegalArgumentException("지원하지 않는 정렬입니다: " + sort);
		};
	}
}
