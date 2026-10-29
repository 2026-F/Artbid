package com.artbid.settlement.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.member.domain.MemberRole;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.exception.SettlementAccessDeniedException;
import com.artbid.settlement.exception.SettlementNotFoundException;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

	private static final Long WINNER_ID = 2L;
	private static final Long CONSIGNOR_ID = 3L;
	private static final Long STRANGER_ID = 4L;

	@Mock
	private SettlementRepository settlementRepository;
	@Mock
	private AuctionRepository auctionRepository;
	@Mock
	private ArtworkRepository artworkRepository;

	private SettlementService service;
	private final Settlement settlement = Settlement.create(5L, WINNER_ID, 100_000, 10_000, 30_000, LocalDateTime.now().plusDays(3));

	@BeforeEach
	void setUp() {
		service = new SettlementService(settlementRepository, auctionRepository, artworkRepository);
	}

	private void consignorIs(Long consignorId) {
		when(auctionRepository.findById(5L)).thenReturn(Optional.of(Auction.builder().id(5L).artworkId(100L).build()));
		Artwork artwork = mock(Artwork.class);
		when(artwork.getConsignorId()).thenReturn(consignorId);
		when(artworkRepository.findById(100L)).thenReturn(Optional.of(artwork));
	}

	@Test
	void 낙찰자는_자기_정산을_볼_수_있다() {
		when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

		assertThat(service.getSettlement(1L, WINNER_ID, MemberRole.BIDDER)).isSameAs(settlement);
		verifyNoInteractions(auctionRepository);
	}

	@Test
	void 작품_위탁자는_정산을_볼_수_있다() {
		when(settlementRepository.findByAuctionId(5L)).thenReturn(Optional.of(settlement));
		consignorIs(CONSIGNOR_ID);

		assertThat(service.getSettlementByAuctionId(5L, CONSIGNOR_ID, MemberRole.CONSIGNOR)).isSameAs(settlement);
	}

	@Test
	void 관리자는_모든_정산을_볼_수_있다() {
		when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

		assertThat(service.getSettlement(1L, STRANGER_ID, MemberRole.ADMIN)).isSameAs(settlement);
	}

	@Test
	void 관계없는_회원은_볼_수_없다() {
		when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
		consignorIs(CONSIGNOR_ID);

		assertThatThrownBy(() -> service.getSettlement(1L, STRANGER_ID, MemberRole.CONSIGNOR))
				.isInstanceOf(SettlementAccessDeniedException.class);
	}

	@Test
	void 정산이_없으면_SettlementNotFoundException() {
		when(settlementRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getSettlement(1L, WINNER_ID, MemberRole.BIDDER))
				.isInstanceOf(SettlementNotFoundException.class);
	}
}
