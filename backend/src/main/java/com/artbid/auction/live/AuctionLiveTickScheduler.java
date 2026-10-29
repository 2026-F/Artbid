package com.artbid.auction.live;

import com.artbid.infra.realtime.AuctionSseRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 구독자가 있는 경매에만 주기적으로 현재가와 잔여시간(auction-tick)을 보낸다.
 * 화면 타이머가 기기 시계에 따라 어긋나는 것을 서버 시각 기준으로 다시 맞추고, SSE 연결을 살아 있게 유지한다.
 * 상태가 바뀔 때는 이 주기를 기다리지 않고 AuctionLiveEventListener가 즉시 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLiveTickScheduler {

	private final AuctionSseRegistry sseRegistry;
	private final AuctionLiveService auctionLiveService;

	@Scheduled(fixedRateString = "${app.auction.live.tick-interval-ms:15000}",
			initialDelayString = "${app.auction.live.tick-interval-ms:15000}")
	public void sendTicks() {
		for (Long auctionId : sseRegistry.activeAuctionIds()) {
			try {
				AuctionLiveView view = auctionLiveService.getView(auctionId, AuctionLiveReason.TICK);
				sseRegistry.broadcastEvent(auctionId, AuctionLiveSseEvents.TICK, view);
			} catch (RuntimeException e) {
				// 없는 경매를 구독했거나 DB 오류. 다른 경매는 계속 보낸다.
				log.debug("[실시간] auctionId={} 주기 동기화 생략: {}", auctionId, e.getMessage());
			}
		}
	}
}
