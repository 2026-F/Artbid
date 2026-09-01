package com.artbid.auction.controller;

import com.artbid.auction.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class BidController {

	private final BidService bidService;

	@PostMapping("/{auctionId}/bids")
	public void submitBid(@PathVariable Long auctionId, @RequestBody BidRequest request) {
		bidService.submitBid(auctionId, request.bidderId(), request.price());
	}

	public record BidRequest(Long bidderId, Long price) {
	}
}
