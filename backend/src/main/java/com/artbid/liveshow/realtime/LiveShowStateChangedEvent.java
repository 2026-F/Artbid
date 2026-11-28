package com.artbid.liveshow.realtime;

import com.artbid.liveshow.dto.LiveShowDtos.ShowStateResponse;

/** 라이브 상태가 바뀌었다는 서버 내부 이벤트. 트랜잭션이 커밋된 뒤에 SSE로 보낸다. */
public record LiveShowStateChangedEvent(ShowStateResponse state) {
}
