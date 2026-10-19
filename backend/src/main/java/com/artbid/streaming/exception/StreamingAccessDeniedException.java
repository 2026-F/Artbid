package com.artbid.streaming.exception;

public class StreamingAccessDeniedException extends RuntimeException {

	public StreamingAccessDeniedException(Long auctionId) {
		super("이 경매의 위탁자만 할 수 있는 작업입니다: auctionId=" + auctionId);
	}
}
