package com.artbid.settlement.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.settlement.domain.SellerPayout;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SellerPayoutRepository;
import com.artbid.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerPayoutServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

	@Mock
	private SettlementRepository settlementRepository;
	@Mock
	private SellerPayoutRepository sellerPayoutRepository;
	@Mock
	private AuctionRepository auctionRepository;
	@Mock
	private ArtworkRepository artworkRepository;

	private SellerPayoutService service;

	@BeforeEach
	void setUp() {
		service = new SellerPayoutService(settlementRepository, sellerPayoutRepository,
				auctionRepository, artworkRepository, new BigDecimal("0.10"));
	}

	private Settlement settlement(boolean paid) {
		Settlement settlement = Settlement.create(5L, 2L, 500_000, 50_000, 30_000, NOW.plusDays(3));
		ReflectionTestUtils.setField(settlement, "id", 1L);
		if (paid) {
			settlement.markPaid();
		}
		return settlement;
	}

	@Test
	void 결제_완료된_정산의_위탁자_지급_대기를_만든다() {
		when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement(true)));
		when(sellerPayoutRepository.existsBySettlementId(1L)).thenReturn(false);
		when(auctionRepository.findById(5L)).thenReturn(Optional.of(Auction.builder().id(5L).artworkId(100L).build()));
		Artwork artwork = mock(Artwork.class);
		when(artwork.getId()).thenReturn(100L);
		when(artwork.getConsignorId()).thenReturn(3L);
		when(artworkRepository.findById(100L)).thenReturn(Optional.of(artwork));

		assertThat(service.createReady(1L, NOW)).isTrue();

		ArgumentCaptor<SellerPayout> saved = ArgumentCaptor.forClass(SellerPayout.class);
		verify(sellerPayoutRepository).save(saved.capture());
		assertThat(saved.getValue().getConsignorId()).isEqualTo(3L);
		assertThat(saved.getValue().getArtworkId()).isEqualTo(100L);
		assertThat(saved.getValue().getPayoutAmount()).isEqualTo(450_000L);
	}

	@Test
	void 이미_지급_대기가_있으면_만들지_않는다() {
		when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement(true)));
		when(sellerPayoutRepository.existsBySettlementId(1L)).thenReturn(true);

		assertThat(service.createReady(1L, NOW)).isFalse();
		verify(sellerPayoutRepository, never()).save(any());
	}

	@Test
	void 결제_완료가_아니면_만들지_않는다() {
		when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement(false)));

		assertThat(service.createReady(1L, NOW)).isFalse();
		verify(sellerPayoutRepository, never()).save(any());
	}

	@Test
	void 수수료율_설정이_범위를_벗어나면_시작할_때_실패한다() {
		assertThatThrownBy(() -> new SellerPayoutService(settlementRepository, sellerPayoutRepository,
				auctionRepository, artworkRepository, new BigDecimal("1.5")))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
