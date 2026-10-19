package com.artbid.streaming.exception;

public class LivestreamAlreadyLiveException extends RuntimeException {

	public LivestreamAlreadyLiveException(Long auctionId) {
		super("이미 진행 중인 방송이 있습니다: auctionId=" + auctionId);
	}
}
