package com.artbid.auction.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionTest {

	private Auction auctionEndingAt(LocalDateTime auctionEndAt) {
		return Auction.builder()
				.id(1L)
				.artworkId(100L)
				.startPrice(10_000L)
				.currentPrice(15_000L)
				.minBidUnit(1_000L)
				.previewStart(LocalDateTime.now().minusDays(1))
				.previewEnd(LocalDateTime.now().minusHours(1))
				.auctionEndAt(auctionEndAt)
				.status(AuctionStatus.ONGOING)
				.build();
	}

	@Test
	void 마감시간이_지나면_CLOSED_상태가_된다() {
		LocalDateTime auctionEndAt = LocalDateTime.now().minusMinutes(1);
		Auction auction = auctionEndingAt(auctionEndAt);

		auction.close(auctionEndAt.plusSeconds(1));

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);
	}

	@Test
	void 마감시간이_지나지_않았으면_예외를_던진다() {
		LocalDateTime auctionEndAt = LocalDateTime.now().plusMinutes(10);
		Auction auction = auctionEndingAt(auctionEndAt);

		assertThatThrownBy(() -> auction.close(LocalDateTime.now()))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ONGOING);
	}

	@Test
	void 이미_마감된_경매는_마감시간_이전이어도_예외없이_그대로_둔다() {
		LocalDateTime auctionEndAt = LocalDateTime.now().plusMinutes(10);
		Auction auction = auctionEndingAt(auctionEndAt);
		ReflectionTestUtils.setField(auction, "status", AuctionStatus.CLOSED);

		auction.close(LocalDateTime.now()); // 마감시간 전이지만 이미 CLOSED이므로 예외 없이 리턴

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);
	}
}
