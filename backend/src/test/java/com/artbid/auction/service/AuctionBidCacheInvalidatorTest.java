package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveChangedEvent;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuctionBidCacheInvalidatorTest {

	private static final Long AUCTION_ID = 7L;

	private final AuctionPriceRedisService priceRedisService = mock(AuctionPriceRedisService.class);
	private final AuctionBidCacheInvalidator invalidator = new AuctionBidCacheInvalidator(priceRedisService);

	private AuctionLiveChangedEvent event(AuctionLiveReason reason) {
		Auction auction = Auction.builder()
				.id(AUCTION_ID)
				.currentPrice(10_000L)
				.minBidUnit(1_000L)
				.auctionEndAt(LocalDateTime.now().plusMinutes(10))
				.status(AuctionStatus.CLOSED)
				.build();
		return new AuctionLiveChangedEvent(AuctionLiveState.snapshot(auction), reason, false);
	}

	// 관리자 상태 변경(SNAPSHOT), 마감(CLOSED)처럼 입찰 밖에서 바뀐 경우엔 캐시를 지운다.
	@ParameterizedTest
	@EnumSource(value = AuctionLiveReason.class, names = {"SNAPSHOT", "CLOSED", "TICK"})
	void 입찰_밖의_변경이면_입찰_판정_캐시를_지운다(AuctionLiveReason reason) {
		invalidator.onChanged(event(reason));

		verify(priceRedisService).invalidate(AUCTION_ID);
	}

	@Test
	void 입찰_이벤트로는_캐시를_지우지_않는다() {
		// 입찰은 Redis를 먼저 갱신하고 오므로, 지우면 매 입찰마다 DB를 다시 읽게 된다
		invalidator.onChanged(event(AuctionLiveReason.BID));

		verify(priceRedisService, never()).invalidate(any());
	}

	@Test
	void 입찰_취소_이벤트는_BidService가_직접_지우므로_여기서는_지우지_않는다() {
		invalidator.onChanged(event(AuctionLiveReason.BID_CANCELED));

		verify(priceRedisService, never()).invalidate(any());
	}

	@Test
	void 캐시_삭제가_실패해도_예외를_밖으로_던지지_않는다() {
		doThrow(new RuntimeException("Redis 연결 실패 시뮬레이션")).when(priceRedisService).invalidate(AUCTION_ID);

		assertThatCode(() -> invalidator.onChanged(event(AuctionLiveReason.CLOSED)))
				.doesNotThrowAnyException();
	}
}
