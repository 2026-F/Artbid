package com.artbid.streaming.exception;

/** 라이브 편성에 들어 있는 경매는 라이브 편성 화면에서 방송한다. 경매 단위로 따로 방송을 열지 않는다. */
public class LivestreamInLiveShowException extends RuntimeException {

	public LivestreamInLiveShowException(Long auctionId) {
		super("라이브 편성에 들어 있는 경매는 경매 단위로 방송을 시작할 수 없습니다. 라이브 편성에서 진행해주세요: auctionId="
				+ auctionId);
	}
}
