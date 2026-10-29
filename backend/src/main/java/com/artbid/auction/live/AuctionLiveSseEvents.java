package com.artbid.auction.live;

/** 실시간 상태를 보내는 SSE 이벤트 이름. 화면은 EventSource.addEventListener(이름)로 받는다. */
public final class AuctionLiveSseEvents {

	/** 상태가 바뀌었을 때와 구독 직후 보내는 현재 상태 */
	public static final String LIVE = "auction-live";
	/** 잔여시간을 맞추기 위한 주기적 동기화 */
	public static final String TICK = "auction-tick";

	private AuctionLiveSseEvents() {
	}
}
