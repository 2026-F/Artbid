package com.artbid.streaming.controller;

import com.artbid.streaming.dto.LivestreamResponse;
import com.artbid.streaming.service.StreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions/{auctionId}/stream")
@RequiredArgsConstructor
public class StreamingController {

	private final StreamingService streamingService;

	/** 스테이지를 만들고 방송을 LIVE로 연다. */
	@PostMapping("/start")
	@ResponseStatus(HttpStatus.CREATED)
	public LivestreamResponse startStream(@PathVariable Long auctionId) {
		return LivestreamResponse.from(streamingService.startStream(auctionId));
	}

	/** 스테이지를 삭제하고 방송을 종료한다. 이미 끝난 방송이어도 204를 돌려준다. */
	@PostMapping("/end")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void endStream(@PathVariable Long auctionId) {
		streamingService.endStream(auctionId);
	}

	/** 방송 상태 조회. 비로그인 시청자도 호출한다. */
	@GetMapping
	public LivestreamResponse getStream(@PathVariable Long auctionId) {
		return LivestreamResponse.from(streamingService.getStream(auctionId));
	}
}
