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
import com.artbid.artwork.exception.ArtworkAuthRequiredException;
import com.artbid.artwork.exception.ArtworkForbiddenException;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.security.Principal;
import java.util.Set;

@RestController
@RequestMapping("/api/artworks")
@RequiredArgsConstructor
public class ArtworkController {

	private static final int MAX_PAGE_SIZE = 100;

	private final ArtworkService artworkService;

	@PostMapping
	public ResponseEntity<ArtworkDetailResponse> register(@Valid @RequestBody ArtworkCreateRequest request,
			Principal principal, HttpServletRequest httpRequest) {
		Long memberId = requireMemberId(principal);
		if (role(httpRequest) != MemberRole.CONSIGNOR) {
			throw new ArtworkForbiddenException("판매자(CONSIGNOR)만 작품을 등록할 수 있습니다.");
		}
		ArtworkDetailResponse response = artworkService.register(memberId, request);
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
	public ArtworkDetailResponse update(@PathVariable Long artworkId, @Valid @RequestBody ArtworkUpdateRequest request,
			Principal principal, HttpServletRequest httpRequest) {
		Long memberId = requireMemberId(principal);
		return artworkService.update(artworkId, memberId, role(httpRequest) == MemberRole.ADMIN, request);
	}

	private Sort toSort(String sort) {
		return switch (sort) {
			case "latest" -> Sort.by(Sort.Direction.DESC, "id");
			case "priceAsc" -> Sort.by(Sort.Order.asc("startPrice"), Sort.Order.desc("id"));
			case "priceDesc" -> Sort.by(Sort.Order.desc("startPrice"), Sort.Order.desc("id"));
			default -> throw new IllegalArgumentException("지원하지 않는 정렬입니다: " + sort);
		};
	}

	@PostMapping("/{artworkId}/approve")
	public ResponseEntity<Void> approve(@PathVariable Long artworkId, HttpServletRequest request){
		requireAdmin(request);
		artworkService.approve(artworkId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{artworkId}/reject")
	public ResponseEntity<Void> reject(@PathVariable Long artworkId, HttpServletRequest request) {
		requireAdmin(request);
		artworkService.reject(artworkId);
		return ResponseEntity.noContent().build();
	}

	// 이미 공개된(PREVIEW/IN_AUCTION) 작품을 다시 비공개로 내린다. (어드민 "경매 상태 관리" 화면의 "작품 내리기" 버튼)
	@PostMapping("/{artworkId}/unpublish")
	public ResponseEntity<Void> unpublish(@PathVariable Long artworkId, HttpServletRequest request) {
		requireAdmin(request);
		artworkService.unpublish(artworkId);
		return ResponseEntity.noContent().build();
	}

	// 로그인한 회원의 role이 ADMIN인지 확인.
	private void requireAdmin(HttpServletRequest request) {
		if (role(request) != MemberRole.ADMIN) {
			throw new ArtworkForbiddenException();
		}
	}

	private Object role(HttpServletRequest request) {
		return request.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE);
	}

	// Principal.name에는 JwtAuthenticationFilter가 검증한 Member ID가 들어 있다 (BidController와 같은 방식).
	private Long requireMemberId(Principal principal) {
		if (principal != null) {
			try {
				long id = Long.parseLong(principal.getName());
				if (id > 0) return id;
			} catch (NumberFormatException ignored) {
				// 아래에서 인증 필요 예외로 처리
			}
		}
		throw new ArtworkAuthRequiredException();
	}


}
