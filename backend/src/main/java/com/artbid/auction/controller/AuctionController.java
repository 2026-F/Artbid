package com.artbid.auction.controller;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

	private final AuctionService auctionService;

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
}
