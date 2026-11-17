package com.artbid.auction.live;

/**
 * 경매의 실시간 상태가 바뀌었다는 서버 내부 이벤트.
 * 지금은 Spring 이벤트로 같은 서버 안에서만 전달한다. 서버가 여러 대가 되면 이 이벤트를 Redis Pub/Sub으로
 * 발행하도록 바꾸고, 받는 쪽(캐시 갱신과 SSE 전송)은 그대로 재사용하는 것을 목표로 한다.
 */
public record AuctionLiveChangedEvent(
		AuctionLiveState state,
		AuctionLiveReason reason,
		boolean extended) {
}
