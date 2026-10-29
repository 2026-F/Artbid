package com.artbid.auction.live;

import com.artbid.auction.domain.AuctionStatus;

import java.time.LocalDateTime;

/**
 * 화면에 내려주는 실시간 상태. 조회 API 응답과 SSE 이벤트(auction-live, auction-tick)에 같은 모양으로 쓴다.
 *
 * @param nextMinPrice     다음 입찰 최소 금액 (현재가 + 최소 입찰 단위)
 * @param remainingSeconds 서버 기준 마감까지 남은 초. 화면은 serverTime과 함께 받아 타이머를 보정한다.
 * @param serverTime       이 값을 만든 서버 시각
 * @param version          상태 변경 순서. 화면은 더 작은 version의 이벤트가 늦게 오면 무시하면 된다.
 * @param extended         이번 변경으로 마감이 연장됐는지 (안티 스나이핑)
 */
public record AuctionLiveView(
		Long auctionId,
		Long currentPrice,
		Long minBidUnit,
		Long nextMinPrice,
		LocalDateTime auctionEndAt,
		AuctionStatus status,
		long remainingSeconds,
		LocalDateTime serverTime,
		long version,
		AuctionLiveReason reason,
		boolean extended) {
}
