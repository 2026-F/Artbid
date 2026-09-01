package com.artbid.infra.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ws/auctions")
@RequiredArgsConstructor
public class RealtimeController {

	private final AuctionSseRegistry sseRegistry;

	@GetMapping(value = "/{id}", produces = "text/event-stream")
	public SseEmitter subscribe(@PathVariable Long id) {
		return sseRegistry.subscribe(id);
	}
}
