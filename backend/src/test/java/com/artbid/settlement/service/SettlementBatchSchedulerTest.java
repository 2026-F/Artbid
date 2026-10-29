package com.artbid.settlement.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementBatchSchedulerTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

	@Mock
	private SettlementExpiryService settlementExpiryService;
	@Mock
	private SellerPayoutService sellerPayoutService;

	private SettlementBatchScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new SettlementBatchScheduler(settlementExpiryService, sellerPayoutService);
	}

	@Test
	void 만료와_지급_대기_생성을_차례로_처리하고_건수를_센다() {
		when(settlementExpiryService.findOverdueIds(NOW)).thenReturn(List.of(1L, 2L));
		when(settlementExpiryService.expireOne(1L, NOW)).thenReturn(true);
		when(settlementExpiryService.expireOne(2L, NOW)).thenReturn(false); // 결과 불명 결제로 보류
		when(sellerPayoutService.findTargetIds()).thenReturn(List.of(3L));
		when(sellerPayoutService.createReady(3L, NOW)).thenReturn(true);

		SettlementBatchScheduler.BatchResult result = scheduler.runOnce(NOW);

		assertThat(result).isEqualTo(new SettlementBatchScheduler.BatchResult(1, 1, 0));
	}

	@Test
	void 한_건이_실패해도_나머지는_처리한다() {
		when(settlementExpiryService.findOverdueIds(NOW)).thenReturn(List.of(1L, 2L));
		when(settlementExpiryService.expireOne(1L, NOW)).thenThrow(new IllegalStateException("DB 오류"));
		when(settlementExpiryService.expireOne(2L, NOW)).thenReturn(true);
		when(sellerPayoutService.findTargetIds()).thenReturn(List.of(3L, 4L));
		when(sellerPayoutService.createReady(3L, NOW)).thenThrow(new IllegalStateException("작품 없음"));
		when(sellerPayoutService.createReady(4L, NOW)).thenReturn(true);

		SettlementBatchScheduler.BatchResult result = scheduler.runOnce(NOW);

		assertThat(result).isEqualTo(new SettlementBatchScheduler.BatchResult(1, 1, 2));
		verify(sellerPayoutService).createReady(4L, NOW);
	}
}
