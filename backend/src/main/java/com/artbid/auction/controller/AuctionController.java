package com.artbid.auction.controller;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.dto.AuctionDetailResponse;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveService;
import com.artbid.auction.live.AuctionLiveView;
import com.artbid.auction.service.AuctionService;
import com.artbid.common.exception.AuctionForbiddenException;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

	private final AuctionService auctionService;
	private final AuctionLiveService auctionLiveService;

	@GetMapping("/{id}/current-price")
	public Auction getCurrentPrice(@PathVariable Long id) {
		return auctionService.getAuction(id);
	}

	@PostMapping
	public Auction createAuction(@RequestBody AuctionCreateRequest request) {
		return auctionService.createAuction(
				request.artworkId(),
				request.startPrice(),
				request.minBidUnit(),
				request.previewStart(),
				request.previewEnd(),
				request.auctionEndAt()
		);
	}

	// BidController의 BidRequest처럼 record로 요청 body를 받는 DTO
	public record AuctionCreateRequest(Long artworkId, Long startPrice, Long minBidUnit,
                                       LocalDateTime previewStart, LocalDateTime previewEnd,
                                       LocalDateTime auctionEndAt) {
	}

	@GetMapping("/{id}")
	public AuctionDetailResponse getAuctionDetail(@PathVariable Long id){
		return auctionService.getAuctionDetail(id);
	}

	// 실시간 현재가와 잔여시간. 캐시 우선, 없으면 DB. SSE를 연결하기 전 첫 화면이나 재연결 때 쓴다.
	@GetMapping("/{id}/live")
	public AuctionLiveView getLive(@PathVariable Long id) {
		return auctionLiveService.getView(id, AuctionLiveReason.SNAPSHOT);
	}

	// 어드민이 경매 상태를 직접 지정 (예: PREVIEW -> ONGOING으로 열기, 강제 CLOSED 등).
	// 지금은 시간에 맞춰 자동으로 상태가 바뀌는 스케줄러가 없어서 이 엔드포인트로 수동 전환한다.
	@PatchMapping("/{id}/status")
	public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestBody AuctionStatusUpdateRequest request,
			HttpServletRequest httpRequest) {
		requireAdmin(httpRequest);
		auctionService.updateStatus(id, request.status());
		return ResponseEntity.noContent().build();
	}

	public record AuctionStatusUpdateRequest(AuctionStatus status) {
	}

	// ArtworkController의 requireAdmin과 같은 패턴 — 로그인한 회원의 role이 ADMIN인지 확인.
	private void requireAdmin(HttpServletRequest request) {
		Object role = request.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE);
		if (role != MemberRole.ADMIN) {
			throw new AuctionForbiddenException();
		}
	}
}
