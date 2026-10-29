package com.artbid.auction.live;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionLiveStateTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0, 0);

	private Auction auction(AuctionStatus status, LocalDateTime endAt) {
		return Auction.builder()
				.id(1L)
				.artworkId(10L)
				.startPrice(10_000L)
				.currentPrice(15_000L)
				.minBidUnit(1_000L)
				.previewStart(NOW.minusDays(2))
				.previewEnd(NOW.minusDays(1))
				.auctionEndAt(endAt)
				.status(status)
				.build();
	}

	@Test
	void 변경_상태는_경매_값과_변경_시각을_담는다() {
		AuctionLiveState state = AuctionLiveState.changed(auction(AuctionStatus.ONGOING, NOW.plusMinutes(5)), NOW);

		assertThat(state.auctionId()).isEqualTo(1L);
		assertThat(state.currentPrice()).isEqualTo(15_000L);
		assertThat(state.auctionEndAt()).isEqualTo(NOW.plusMinutes(5));
		assertThat(state.status()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(state.version()).isPositive();
	}

	@Test
	void 나중에_바뀐_상태의_version이_더_크다() {
		Auction auction = auction(AuctionStatus.ONGOING, NOW.plusMinutes(5));

		long earlier = AuctionLiveState.changed(auction, NOW).version();
		long later = AuctionLiveState.changed(auction, NOW.plusNanos(1_000)).version();

		assertThat(later).isGreaterThan(earlier);
	}

	@Test
	void 스냅샷의_version은_0이다() {
		AuctionLiveState state = AuctionLiveState.snapshot(auction(AuctionStatus.ONGOING, NOW.plusMinutes(5)));

		assertThat(state.version()).isEqualTo(AuctionLiveState.SNAPSHOT_VERSION);
	}

	@Test
	void 잔여시간은_마감까지_남은_초다() {
		AuctionLiveState state = AuctionLiveState.snapshot(auction(AuctionStatus.ONGOING, NOW.plusSeconds(90)));

		assertThat(state.remainingSeconds(NOW)).isEqualTo(90L);
		assertThat(state.remainingSeconds(NOW.plusNanos(500_000_000))).isEqualTo(89L); // 남은 89.5초는 89초로 내림
	}

	@Test
	void 마감_시각이_지났으면_잔여시간은_0이다() {
		AuctionLiveState state = AuctionLiveState.snapshot(auction(AuctionStatus.ONGOING, NOW.minusSeconds(1)));

		assertThat(state.remainingSeconds(NOW)).isZero();
	}

	@Test
	void 마감된_경매는_잔여시간이_0이다() {
		AuctionLiveState state = AuctionLiveState.snapshot(auction(AuctionStatus.CLOSED, NOW.plusMinutes(5)));

		assertThat(state.remainingSeconds(NOW)).isZero();
	}

	@Test
	void 화면용_값에는_다음_최소_입찰가와_서버_시각이_들어간다() {
		AuctionLiveState state = AuctionLiveState.changed(auction(AuctionStatus.EXTENDED, NOW.plusSeconds(30)), NOW);

		AuctionLiveView view = state.toView(NOW, AuctionLiveReason.BID, true);

		assertThat(view.nextMinPrice()).isEqualTo(16_000L);
		assertThat(view.remainingSeconds()).isEqualTo(30L);
		assertThat(view.serverTime()).isEqualTo(NOW);
		assertThat(view.version()).isEqualTo(state.version());
		assertThat(view.reason()).isEqualTo(AuctionLiveReason.BID);
		assertThat(view.extended()).isTrue();
	}
}
