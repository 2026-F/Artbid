package com.artbid.auction.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.artwork.exception.ArtworkNotEditableException;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.common.exception.AuctionCreationConflictException;
import com.artbid.common.exception.InvalidAuctionRequestException;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.PaymentStatus;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuctionReopenServiceTest {

	private static final LocalDateTime PAST = LocalDateTime.now().minusDays(2);

	private AuctionRepository auctions;
	private SettlementRepository settlements;
	private PaymentRepository payments;
	private AuctionReopenService service;
	private Artwork artwork;
	private Auction closed;

	@BeforeEach
	void setUp() {
		auctions = mock(AuctionRepository.class);
		settlements = mock(SettlementRepository.class);
		payments = mock(PaymentRepository.class);
		ArtworkRepository artworks = mock(ArtworkRepository.class);
		service = new AuctionReopenService(auctions, artworks, settlements, payments);

		artwork = Artwork.builder().consignorId(1L).artistId(2L).title("무제")
				.category(ArtworkCategory.PAINTING).startPrice(100_000L).build();
		ReflectionTestUtils.setField(artwork, "id", 10L);
		artwork.approve();
		artwork.markInAuction();
		when(artworks.findById(10L)).thenReturn(Optional.of(artwork));

		closed = Auction.create(10L, 100_000L, 10_000L, PAST, PAST.plusHours(1), PAST.plusHours(2), PAST);
		ReflectionTestUtils.setField(closed, "id", 5L);
		ReflectionTestUtils.setField(closed, "currentPrice", 300_000L);
		closed.overrideStatus(AuctionStatus.CLOSED);
		when(auctions.findByIdForUpdate(5L)).thenReturn(Optional.of(closed));
		when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of(closed));
		when(auctions.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(settlements.findAllByAuctionIdForUpdate(5L)).thenReturn(List.of());
	}

	private Settlement settlement() {
		Settlement settlement = Settlement.create(5L, 7L, 300_000L, 30_000L, 30_000L, LocalDateTime.now().plusDays(3));
		ReflectionTestUtils.setField(settlement, "id", 20L);
		when(settlements.findAllByAuctionIdForUpdate(5L)).thenReturn(List.of(settlement));
		return settlement;
	}

	@Test
	void 유찰된_경매를_시작가부터_진행_중인_새_경매로_연다() {
		Auction reopened = service.reopen(5L, 60);

		assertThat(reopened).isNotSameAs(closed);
		assertThat(reopened.getArtworkId()).isEqualTo(10L);
		assertThat(reopened.getStatus()).isEqualTo(AuctionStatus.ONGOING);
		assertThat(reopened.getCurrentPrice()).isEqualTo(100_000L);
		assertThat(reopened.getMinBidUnit()).isEqualTo(10_000L);
		assertThat(reopened.getAuctionEndAt()).isAfter(LocalDateTime.now().plusMinutes(59));
		assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.IN_AUCTION);
		assertThat(closed.getStatus()).isEqualTo(AuctionStatus.CLOSED);
	}

	@Test
	void 미결제_낙찰은_정산을_무효로_하고_작품을_다시_연다() {
		artwork.markSold();
		Settlement settlement = settlement();

		service.reopen(5L, 60);

		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(settlement.getExpiredAt()).isNull();
		assertThat(settlement.isPayableAt(LocalDateTime.now())).isFalse();
		assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.IN_AUCTION);
	}

	@Test
	void 결제_완료된_경매는_다시_열_수_없다() {
		artwork.markSold();
		Settlement settlement = settlement();
		settlement.markPaid();

		assertThatThrownBy(() -> service.reopen(5L, 60)).isInstanceOf(AuctionCreationConflictException.class);

		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.SOLD);
		verify(auctions, never()).save(any());
	}

	@Test
	void 결과를_모르는_결제가_있으면_다시_열_수_없다() {
		artwork.markSold();
		Settlement settlement = settlement();
		when(payments.existsBySettlementIdAndStatusIn(eq(20L), anyCollection())).thenReturn(true);

		assertThatThrownBy(() -> service.reopen(5L, 60)).isInstanceOf(AuctionCreationConflictException.class);

		assertThat(settlement.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
		verify(auctions, never()).save(any());
	}

	@Test
	void 마감되지_않은_경매는_다시_열_수_없다() {
		closed.overrideStatus(AuctionStatus.ONGOING);

		assertThatThrownBy(() -> service.reopen(5L, 60)).isInstanceOf(AuctionCreationConflictException.class);
		verify(auctions, never()).save(any());
	}

	@Test
	void 이미_새_경매가_있으면_다시_열_수_없다() {
		Auction newer = mock(Auction.class);
		when(newer.getId()).thenReturn(6L);
		when(newer.getStatus()).thenReturn(AuctionStatus.ONGOING);
		when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of(closed, newer));

		assertThatThrownBy(() -> service.reopen(5L, 60)).isInstanceOf(AuctionCreationConflictException.class);
		verify(auctions, never()).save(any());
	}

	@Test
	void 이전_회차_경매는_다시_열_수_없다() {
		Auction newerClosed = mock(Auction.class);
		when(newerClosed.getId()).thenReturn(6L);
		when(newerClosed.getStatus()).thenReturn(AuctionStatus.CLOSED);
		when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of(closed, newerClosed));

		assertThatThrownBy(() -> service.reopen(5L, 60)).isInstanceOf(AuctionCreationConflictException.class);
		verify(auctions, never()).save(any());
	}

	@Test
	void 비공개로_내린_작품은_다시_열_수_없다() {
		artwork.unpublish();

		assertThatThrownBy(() -> service.reopen(5L, 60)).isInstanceOf(ArtworkNotEditableException.class);
		verify(auctions, never()).save(any());
	}

	@Test
	void 진행_시간이_범위를_벗어나면_거절한다() {
		for (Integer minutes : new Integer[]{null, 0, -1, 60 * 24 * 30 + 1}) {
			assertThatThrownBy(() -> service.reopen(5L, minutes)).isInstanceOf(InvalidAuctionRequestException.class);
		}
		verifyNoInteractions(settlements);
	}
}
