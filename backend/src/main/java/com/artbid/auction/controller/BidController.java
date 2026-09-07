package com.artbid.auction.controller;

import com.artbid.auction.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class BidController {

	private final BidService bidService;

	@PostMapping("/{auctionId}/bids")
	public BidService.BidResult submitBid(@PathVariable Long auctionId, @RequestBody BidRequest request) {
		return bidService.submitBid(auctionId, request.bidderId(), request.price());
	}

	public record BidRequest(Long bidderId, Long price) {
	}
}