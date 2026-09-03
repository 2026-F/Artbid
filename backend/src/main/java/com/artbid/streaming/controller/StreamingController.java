package com.artbid.streaming.controller;

import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.service.StreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions/{auctionId}/stream")
@RequiredArgsConstructor
public class StreamingController {

	private final StreamingService streamingService;

	@PostMapping("/start")
	public Livestream startStream(@PathVariable Long auctionId) {
		return streamingService.startStream(auctionId);
	}

	@PostMapping("/end")
	public void endStream(@PathVariable Long auctionId) {
		streamingService.endStream(auctionId);
	}

	@GetMapping
	public Livestream getStream(@PathVariable Long auctionId) {
		return streamingService.getStream(auctionId);
	}
}
