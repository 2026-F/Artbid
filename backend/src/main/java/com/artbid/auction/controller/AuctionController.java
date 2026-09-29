package com.artbid.auction.controller;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
	public Auction creatAuction(@RequestBody AuctionCreateRequest request){
		return auctionService.createAuction(
				request.artwordId,

		)
	}
}
