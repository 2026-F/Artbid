package com.artbid.auction.live;

import com.artbid.auction.domain.AuctionStatus;
import com.artbid.infra.realtime.AuctionSseRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionLiveEventListenerTest {

	@Mock
	private AuctionLiveCache cache;
	@Mock
	private AuctionSseRegistry sseRegistry;

	@Test
	void 변경_이벤트를_받으면_캐시를_갱신한_뒤_구독자에게_보낸다() {
		AuctionLiveEventListener listener = new AuctionLiveEventListener(cache, sseRegistry);
		AuctionLiveState state = new AuctionLiveState(1L, 16_000L, 1_000L,
				LocalDateTime.now().plusMinutes(5), AuctionStatus.EXTENDED, 123L);

		listener.onChanged(new AuctionLiveChangedEvent(state, AuctionLiveReason.BID, true));

		InOrder order = inOrder(cache, sseRegistry);
		order.verify(cache).saveChange(state);
		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		order.verify(sseRegistry).broadcastEvent(eq(1L), eq(AuctionLiveSseEvents.LIVE), payload.capture());

		AuctionLiveView view = (AuctionLiveView) payload.getValue();
		assertThat(view.currentPrice()).isEqualTo(16_000L);
		assertThat(view.nextMinPrice()).isEqualTo(17_000L);
		assertThat(view.reason()).isEqualTo(AuctionLiveReason.BID);
		assertThat(view.extended()).isTrue();
		assertThat(view.version()).isEqualTo(123L);
	}
}
