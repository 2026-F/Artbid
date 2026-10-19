package com.artbid.auction.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.infra.realtime.AuctionSseRegistry;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingServiceTest {

	@Mock
	private AuctionRepository auctionRepository;
	@Mock
	private ArtworkRepository artworkRepository;
	@Mock
	private BidRepository bidRepository;
	@Mock
	private SettlementRepository settlementRepository;
	@Mock
	private AuctionSseRegistry sseRegistry;

	private AuctionClosingService auctionClosingService;

	private static final Long AUCTION_ID = 1L;
	private static final Long ARTWORK_ID = 100L;
	private static final Long BIDDER_ID = 10L;

	@BeforeEach
	void setUp() {
		auctionClosingService = new AuctionClosingService(
				auctionRepository, artworkRepository, bidRepository, settlementRepository, sseRegistry);
	}

	private Auction auctionWithCurrentPrice(Long currentPrice, LocalDateTime auctionEndAt) {
		return Auction.builder()
				.id(AUCTION_ID)
				.artworkId(ARTWORK_ID)
				.startPrice(10_000L)
				.currentPrice(currentPrice)
				.minBidUnit(1_000L)
				.previewStart(LocalDateTime.now().minusDays(1))
				.previewEnd(LocalDateTime.now().minusHours(1))
				.auctionEndAt(auctionEndAt)
				.status(AuctionStatus.ONGOING)
				.build();
	}

	private Artwork artworkInAuction() {
		Artwork artwork = Artwork.builder()
				.consignorId(1L)
				.artistId(2L)
				.title("무제")
				.category(ArtworkCategory.PAINTING)
				.startPrice(10_000L)
				.build();
		ReflectionTestUtils.setField(artwork, "status", ArtworkStatus.IN_AUCTION);
		return artwork;
	}

	@Test
	void 낙찰자가_있으면_정산을_생성하고_작품을_판매완료로_바꾸고_마감알림을_보낸다() {
		LocalDateTime now = LocalDateTime.now();
		Auction auction = auctionWithCurrentPrice(15_000L, now.minusMinutes(1));
		Bid winningBid = new Bid(AUCTION_ID, BIDDER_ID, 15_000L, now.minusMinutes(2));
		Artwork artwork = artworkInAuction();

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class))).thenReturn(Optional.of(winningBid));
		when(artworkRepository.findById(ARTWORK_ID)).thenReturn(Optional.of(artwork));

		auctionClosingService.closeOne(AUCTION_ID, now);

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);
		assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.SOLD);

		ArgumentCaptor<Settlement> settlementCaptor = ArgumentCaptor.forClass(Settlement.class);
		verify(settlementRepository).save(settlementCaptor.capture());
		Settlement settlement = settlementCaptor.getValue();
		assertThat(settlement.getWinnerId()).isEqualTo(BIDDER_ID);
		assertThat(settlement.getFinalPrice()).isEqualTo(15_000L);
		assertThat(settlement.getTotalAmount()).isEqualTo(15_000L + 1_500L + 30_000L); // 낙찰가 + 10% 수수료 + 배송비

		verify(sseRegistry).broadcastAfterCommit(eq(AUCTION_ID), any(AuctionClosingService.AuctionClosedEvent.class));
	}

	@Test
	void 입찰이_없으면_경매만_마감되고_정산은_생성하지_않는다() {
		LocalDateTime now = LocalDateTime.now();
		Auction auction = auctionWithCurrentPrice(10_000L, now.minusMinutes(1));

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
		when(bidRepository.findTopBid(eq(AUCTION_ID), any(Limit.class))).thenReturn(Optional.empty());

		auctionClosingService.closeOne(AUCTION_ID, now);

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CLOSED);
		verify(settlementRepository, never()).save(any());
		verify(artworkRepository, never()).findById(any());
		verify(sseRegistry).broadcastAfterCommit(eq(AUCTION_ID), any(AuctionClosingService.AuctionClosedEvent.class));
	}

	@Test
	void 이미_마감된_경매는_그대로_리턴하고_아무_처리도_하지_않는다() {
		LocalDateTime now = LocalDateTime.now();
		Auction auction = auctionWithCurrentPrice(10_000L, now.minusMinutes(1));
		ReflectionTestUtils.setField(auction, "status", AuctionStatus.CLOSED);

		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));

		auctionClosingService.closeOne(AUCTION_ID, now);

		verify(bidRepository, never()).findTopBid(any(), any());
		verify(sseRegistry, never()).broadcastAfterCommit(any(), any());
	}

	@Test
	void 경매를_찾을수없으면_예외를_던진다() {
		when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> auctionClosingService.closeOne(AUCTION_ID, LocalDateTime.now()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
