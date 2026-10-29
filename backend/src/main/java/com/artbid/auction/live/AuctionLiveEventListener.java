package com.artbid.auction.live;

import com.artbid.infra.realtime.AuctionSseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 실시간 상태 변경 이벤트를 받아 캐시를 갱신하고 구독자에게 보낸다.
 *
 * 트랜잭션이 커밋된 뒤에만 실행한다. 롤백된 입찰이 캐시나 화면에 반영되면 안 되기 때문이다.
 * 트랜잭션 밖에서 발행한 이벤트(마감 스케줄러 등)는 fallbackExecution으로 바로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class AuctionLiveEventListener {

	private final AuctionLiveCache cache;
	private final AuctionSseRegistry sseRegistry;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onChanged(AuctionLiveChangedEvent event) {
		AuctionLiveState state = event.state();
		cache.saveChange(state);

		AuctionLiveView view = state.toView(LocalDateTime.now(), event.reason(), event.extended());
		sseRegistry.broadcastEvent(state.auctionId(), AuctionLiveSseEvents.LIVE, view);
	}
}
