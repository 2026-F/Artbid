package com.artbid.liveshow.realtime;

/** 라이브 편성 SSE(GET /ws/live-shows/{showId}) 이벤트 이름 */
public final class LiveShowSseEvents {

	/** 라이브 상태와 현재 작품(ShowStateResponse). 구독 직후, 시작, 다음 작품 열기, 종료 때 보낸다 */
	public static final String STATE = "show-state";

	/** 새 채팅 메시지 한 건(ChatMessageResponse) */
	public static final String CHAT = "show-chat";

	private LiveShowSseEvents() {
	}
}
