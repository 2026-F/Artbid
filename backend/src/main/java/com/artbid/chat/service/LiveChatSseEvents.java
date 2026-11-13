package com.artbid.chat.service;

/** 채팅 SSE 이벤트 이름. 경매 실시간 구독(GET /ws/auctions/{id})에 같이 실어 보낸다. */
public final class LiveChatSseEvents {

	/** 새 채팅 메시지 한 건 (ChatMessageResponse) */
	public static final String CHAT = "auction-chat";

	private LiveChatSseEvents() {
	}
}
