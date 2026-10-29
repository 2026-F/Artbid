package com.artbid.streaming.exception;

public class LivestreamNotFoundException extends RuntimeException {

	public LivestreamNotFoundException(Long auctionId) {
		super("방송 정보를 찾을 수 없습니다: auctionId=" + auctionId);
	}
}
