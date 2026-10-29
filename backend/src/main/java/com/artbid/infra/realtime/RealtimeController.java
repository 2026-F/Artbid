package com.artbid.infra.realtime;

import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveService;
import com.artbid.auction.live.AuctionLiveSseEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/ws/auctions")
@RequiredArgsConstructor
public class RealtimeController {

	private final AuctionSseRegistry sseRegistry;
	private final AuctionLiveService auctionLiveService;

	@GetMapping(value = "/{id}", produces = "text/event-stream")
	public SseEmitter subscribe(@PathVariable Long id) {
		SseEmitter emitter = sseRegistry.subscribe(id);
		sendSnapshot(id, emitter);
		return emitter;
	}

	// 구독하자마자 현재 상태를 한 번 보낸다. 다음 입찰이나 동기화 주기를 기다리지 않고 바로 화면을 맞추기 위함.
	// 상태를 못 읽어도(없는 경매, DB 오류) 구독 자체는 유지한다. 기존 동작과 같다.
	private void sendSnapshot(Long auctionId, SseEmitter emitter) {
		try {
			sseRegistry.sendEvent(emitter, AuctionLiveSseEvents.LIVE,
					auctionLiveService.getView(auctionId, AuctionLiveReason.SNAPSHOT));
		} catch (RuntimeException e) {
			log.debug("[실시간] auctionId={} 구독 직후 상태 전송 생략: {}", auctionId, e.getMessage());
		}
	}
}
