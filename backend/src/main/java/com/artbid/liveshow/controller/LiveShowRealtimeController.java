package com.artbid.liveshow.controller;

import com.artbid.liveshow.exception.LiveShowNotFoundException;
import com.artbid.liveshow.realtime.LiveShowSseEvents;
import com.artbid.liveshow.realtime.LiveShowSseRegistry;
import com.artbid.liveshow.service.LiveShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 라이브 편성 실시간 구독. 누구나 구독할 수 있다.
 * 이벤트: show-state(상태와 현재 작품), show-chat(채팅). 현재가와 남은 시간은 기존 /ws/auctions/{auctionId}를 쓴다.
 */
@Slf4j
@RestController
@RequestMapping("/ws/live-shows")
@RequiredArgsConstructor
public class LiveShowRealtimeController {

	private final LiveShowSseRegistry sseRegistry;
	private final LiveShowService liveShowService;

	@GetMapping(value = "/{showId}", produces = "text/event-stream")
	public SseEmitter subscribe(@PathVariable Long showId) {
		SseEmitter emitter = sseRegistry.subscribe(showId);
		sendSnapshot(showId, emitter);
		return emitter;
	}

	// 구독 직후 현재 상태를 한 번 보내, 재연결한 화면이 끊긴 동안 놓친 작품 전환을 따라잡게 한다.
	// 없는 라이브면 연결을 바로 닫는다. DB 오류 등으로 못 읽으면 경매 SSE와 같이 전송만 건너뛰고 구독은 유지한다.
	private void sendSnapshot(Long showId, SseEmitter emitter) {
		try {
			sseRegistry.sendEvent(emitter, LiveShowSseEvents.STATE, liveShowService.getState(showId));
		} catch (LiveShowNotFoundException e) {
			sseRegistry.unregister(showId, emitter);
			emitter.complete();
		} catch (RuntimeException e) {
			log.debug("[실시간] liveShowId={} 구독 직후 상태 전송 생략: {}", showId, e.getMessage());
		}
	}
}
