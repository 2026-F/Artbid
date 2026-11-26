package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveService;
import com.artbid.auction.repository.AuctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingSchedulerTest {

	@Mock
	private AuctionRepository auctionRepository;
	@Mock
	private AuctionClosingService auctionClosingService;
	@Mock
	private AuctionLiveService auctionLiveService;

	private AuctionClosingScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new AuctionClosingScheduler(auctionRepository, auctionClosingService, auctionLiveService);
	}

	private Auction auction(Long id) {
		return Auction.builder().id(id).build();
	}

	@Test
	void 마감에_성공한_경매는_실시간_상태를_CLOSED로_알린다() {
		when(auctionRepository.findEndedAuctions(any(LocalDateTime.class))).thenReturn(List.of(auction(1L)));

		scheduler.closeEndedAuctions();

		verify(auctionClosingService).closeOne(eq(1L), any(LocalDateTime.class));
		verify(auctionLiveService).publishCurrent(1L, AuctionLiveReason.CLOSED);
	}

	@Test
	void 마감에_실패한_경매는_실시간_상태를_알리지_않는다() {
		when(auctionRepository.findEndedAuctions(any(LocalDateTime.class))).thenReturn(List.of(auction(1L), auction(2L)));
		doThrow(new IllegalStateException("DB 오류")).when(auctionClosingService).closeOne(eq(1L), any(LocalDateTime.class));

		scheduler.closeEndedAuctions();

		verify(auctionLiveService, never()).publishCurrent(1L, AuctionLiveReason.CLOSED);
		verify(auctionLiveService).publishCurrent(2L, AuctionLiveReason.CLOSED); // 다른 경매는 계속 처리
	}

	@Test
	void 실시간_상태_알림이_실패해도_예외가_밖으로_나오지_않는다() {
		when(auctionRepository.findEndedAuctions(any(LocalDateTime.class))).thenReturn(List.of(auction(1L)));
		doThrow(new IllegalArgumentException("경매 없음")).when(auctionLiveService).publishCurrent(1L, AuctionLiveReason.CLOSED);

		assertThatCode(() -> scheduler.closeEndedAuctions()).doesNotThrowAnyException();
	}
}
