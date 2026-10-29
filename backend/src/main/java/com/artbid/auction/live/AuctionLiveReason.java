package com.artbid.auction.live;

/**
 * 실시간 상태를 내보낸 이유.
 * 화면은 이 값으로 "입찰이 들어와서 바뀐 것"인지 "주기적인 시간 동기화"인지 구분할 수 있다.
 */
public enum AuctionLiveReason {
	SNAPSHOT,     // 조회 API 응답이나 SSE 구독 직후 보내는 현재 상태
	TICK,         // 구독 중인 화면의 잔여시간을 맞추기 위한 주기적 동기화
	BID,          // 입찰로 현재가(또는 마감 연장) 변경
	BID_CANCELED, // 입찰 취소로 현재가 되돌림
	CLOSED        // 경매 마감
}
