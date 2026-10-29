package com.artbid.infra.realtime;

import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveService;
import com.artbid.auction.live.AuctionLiveSseEvents;
import com.artbid.auction.live.AuctionLiveView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeControllerTest {

	@Mock
	private AuctionSseRegistry sseRegistry;
	@Mock
	private AuctionLiveService auctionLiveService;

	private RealtimeController controller;
	private final SseEmitter emitter = new SseEmitter(0L);

	@BeforeEach
	void setUp() {
		controller = new RealtimeController(sseRegistry, auctionLiveService);
		when(sseRegistry.subscribe(1L)).thenReturn(emitter);
	}

	@Test
	void 구독하면_현재_상태를_바로_보낸다() {
		LocalDateTime now = LocalDateTime.now();
		AuctionLiveView view = new AuctionLiveView(1L, 15_000L, 1_000L, 16_000L, now.plusMinutes(1),
				AuctionStatus.ONGOING, 60L, now, 0L, AuctionLiveReason.SNAPSHOT, false);
		when(auctionLiveService.getView(1L, AuctionLiveReason.SNAPSHOT)).thenReturn(view);

		SseEmitter result = controller.subscribe(1L);

		assertThat(result).isSameAs(emitter);
		verify(sseRegistry).sendEvent(emitter, AuctionLiveSseEvents.LIVE, view);
	}

	@Test
	void 상태를_못_읽어도_구독은_유지한다() {
		when(auctionLiveService.getView(1L, AuctionLiveReason.SNAPSHOT))
				.thenThrow(new IllegalArgumentException("경매를 찾을 수 없습니다: 1"));

		SseEmitter result = controller.subscribe(1L);

		assertThat(result).isSameAs(emitter);
		verify(sseRegistry, never()).sendEvent(any(), anyString(), any());
	}
}
