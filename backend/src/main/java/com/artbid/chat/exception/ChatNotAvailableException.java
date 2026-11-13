package com.artbid.chat.exception;

/** 방송이 열려 있지 않은 경매에 채팅을 보내려 했다. */
public class ChatNotAvailableException extends RuntimeException {

	public ChatNotAvailableException(Long auctionId) {
		super("진행 중인 방송이 없어 채팅을 보낼 수 없습니다. auctionId=" + auctionId);
	}
}
