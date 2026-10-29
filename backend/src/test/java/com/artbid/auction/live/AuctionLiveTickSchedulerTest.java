package com.artbid.auction.live;

import com.artbid.auction.domain.AuctionStatus;
import com.artbid.infra.realtime.AuctionSseRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionLiveTickSchedulerTest {

	@Mock
	private AuctionSseRegistry sseRegistry;
	@Mock
	private AuctionLiveService auctionLiveService;

	private AuctionLiveTickScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new AuctionLiveTickScheduler(sseRegistry, auctionLiveService);
	}

	private AuctionLiveView view(Long auctionId) {
		LocalDateTime now = LocalDateTime.now();
		return new AuctionLiveView(auctionId, 15_000L, 1_000L, 16_000L, now.plusMinutes(1),
				AuctionStatus.ONGOING, 60L, now, 0L, AuctionLiveReason.TICK, false);
	}

	@Test
	void 구독자가_있는_경매마다_잔여시간을_보낸다() {
		when(sseRegistry.activeAuctionIds()).thenReturn(Set.of(1L));
		AuctionLiveView view = view(1L);
		when(auctionLiveService.getView(1L, AuctionLiveReason.TICK)).thenReturn(view);

		scheduler.sendTicks();

		verify(sseRegistry).broadcastEvent(1L, AuctionLiveSseEvents.TICK, view);
	}

	@Test
	void 구독자가_없으면_아무것도_보내지_않는다() {
		when(sseRegistry.activeAuctionIds()).thenReturn(Set.of());

		scheduler.sendTicks();

		verify(sseRegistry, never()).broadcastEvent(anyLong(), anyString(), any());
	}

	@Test
	void 한_경매가_실패해도_나머지는_보낸다() {
		when(sseRegistry.activeAuctionIds()).thenReturn(new LinkedHashSet<>(List.of(1L, 2L)));
		when(auctionLiveService.getView(1L, AuctionLiveReason.TICK))
				.thenThrow(new IllegalArgumentException("경매를 찾을 수 없습니다: 1"));
		AuctionLiveView view = view(2L);
		when(auctionLiveService.getView(2L, AuctionLiveReason.TICK)).thenReturn(view);

		scheduler.sendTicks();

		verify(sseRegistry).broadcastEvent(2L, AuctionLiveSseEvents.TICK, view);
	}
}
