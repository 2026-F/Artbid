package com.artbid.settlement.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.event.AuctionClosedMessage;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 경매 마감 이벤트로 정산을 만드는 서비스 테스트.
 * 핵심은 같은 이벤트가 여러 번 와도 정산이 하나만 생기는지(멱등성), 다시 열린 경매가 있으면 옛 경매 정산을 만들지 않는지다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementCreationServiceTest {

	@Mock
	private SettlementRepository settlementRepository;
	@Mock
	private ArtworkRepository artworkRepository;
	@Mock
	private AuctionRepository auctionRepository;
	@InjectMocks
	private SettlementCreationService service;

	private static final Long AUCTION_ID = 5L;
	private static final Long ARTWORK_ID = 100L;
	private static final Long BIDDER_ID = 10L;
	private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 11, 16, 12, 0);

	private AuctionClosedMessage message() {
		return new AuctionClosedMessage(AUCTION_ID, BIDDER_ID, ARTWORK_ID, 15_000L, CLOSED_AT);
	}

	private Auction auction(Long id) {
		return Auction.builder().id(id).artworkId(ARTWORK_ID).build();
	}

	private Artwork artworkInAuction() {
		Artwork artwork = Artwork.builder()
				.consignorId(1L).artistId(2L).title("무제")
				.category(ArtworkCategory.PAINTING).startPrice(10_000L)
				.build();
		ReflectionTestUtils.setField(artwork, "status", ArtworkStatus.IN_AUCTION);
		return artwork;
	}

	@Test
	void 마감_이벤트로_정산을_만들고_작품을_판매완료로_바꾼다() {
		Artwork artwork = artworkInAuction();
		when(settlementRepository.existsByAuctionId(AUCTION_ID)).thenReturn(false);
		when(auctionRepository.findByArtworkIdIn(List.of(ARTWORK_ID))).thenReturn(List.of(auction(AUCTION_ID)));
		when(artworkRepository.findById(ARTWORK_ID)).thenReturn(Optional.of(artwork));

		boolean created = service.createForClosedAuction(message());

		assertThat(created).isTrue();
		ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
		verify(settlementRepository).saveAndFlush(captor.capture());
		Settlement settlement = captor.getValue();
		assertThat(settlement.getAuctionId()).isEqualTo(AUCTION_ID);
		assertThat(settlement.getWinnerId()).isEqualTo(BIDDER_ID);
		assertThat(settlement.getTotalAmount()).isEqualTo(15_000L + 1_500L + 30_000L); // 낙찰가 + 10% 수수료 + 배송비
		// 결제 기한은 처리 시각이 아니라 마감 시각 기준 3일 뒤 (재처리돼도 기한이 밀리지 않는다)
		assertThat(settlement.getPaymentDeadlineAt()).isEqualTo(CLOSED_AT.plusDays(3));
		assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.SOLD);
	}

	@Test
	void 같은_경매의_정산이_이미_있으면_아무것도_하지_않는다() {
		// 같은 이벤트가 다시 발행되거나, 다른 소비자가 pending 메시지를 다시 가져온 경우
		when(settlementRepository.existsByAuctionId(AUCTION_ID)).thenReturn(true);

		boolean created = service.createForClosedAuction(message());

		assertThat(created).isFalse();
		verify(settlementRepository, never()).saveAndFlush(any());
		verify(artworkRepository, never()).findById(any());
	}

	@Test
	void 같은_작품으로_더_최근_경매가_열렸으면_옛_경매의_정산은_만들지_않는다() {
		// 이벤트가 늦게 처리되는 사이 관리자가 경매를 다시 연 경우. 옛 경매 정산을 만들면 진행 중인 경매의 작품이 SOLD가 된다.
		when(settlementRepository.existsByAuctionId(AUCTION_ID)).thenReturn(false);
		when(auctionRepository.findByArtworkIdIn(List.of(ARTWORK_ID)))
				.thenReturn(List.of(auction(AUCTION_ID), auction(AUCTION_ID + 1)));

		boolean created = service.createForClosedAuction(message());

		assertThat(created).isFalse();
		verify(settlementRepository, never()).saveAndFlush(any());
		verify(artworkRepository, never()).findById(any());
	}
}
