package com.artbid.auction.domain;

//경매 진행 상태
public enum AuctionStatus {
	SCHEDULED, PREVIEW, ONGOING, EXTENDED, CLOSED
	// SCHEDULED 경매는 등록됐지만 프리뷰 등록 전
	// PREVIEW 미리보기 상태
	// ONGOING 실제 입찰 중
	// EXTENDED 연장
	// CLOSED 경매 마감
}
