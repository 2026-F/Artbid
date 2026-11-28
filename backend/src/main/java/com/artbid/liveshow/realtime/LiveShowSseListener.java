package com.artbid.liveshow.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 라이브 상태 변경을 커밋 후에 구독자에게 보낸다. 롤백된 전환(예: 동시 요청으로 실패한 다음 작품 열기)은 알리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LiveShowSseListener {

	private final LiveShowSseRegistry registry;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStateChanged(LiveShowStateChangedEvent event) {
		registry.broadcastEvent(event.state().showId(), LiveShowSseEvents.STATE, event.state());
	}
}
