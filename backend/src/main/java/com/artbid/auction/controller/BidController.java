package com.artbid.auction.controller;

import com.artbid.auction.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class BidController {

	private final BidService bidService;

	@PostMapping("/{auctionId}/bids")
	public BidService.BidResult submitBid(@PathVariable Long auctionId, @RequestBody BidRequest request) {
		return bidService.submitBid(auctionId, request.bidderId(), request.price());
	}

	@DeleteMapping("/{auctionId}/bids")
	public BidService.BidResult cancelBid(@PathVariable Long auctionId, @RequestBody BidCancelRequest request){
		return bidService.cancelBid(auctionId, request.bidderId());
	}

	@GetMapping("/{auctionId}/bids")
	public List<BidService.BidResponse> getBids(@PathVariable Long auctionId){
		return bidService.getBids(auctionId);
	}

	public record BidRequest(Long bidderId, Long price) {
	}

	public record BidCancelRequest(Long bidderId) {
	}
}