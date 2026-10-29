package com.artbid.auction.controller;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.dto.AuctionDetailResponse;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveService;
import com.artbid.auction.live.AuctionLiveView;
import com.artbid.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
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
}
