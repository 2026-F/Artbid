package com.artbid.auction.live;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionLiveServiceTest {

	@Mock
	private AuctionLiveCache cache;
	@Mock
	private AuctionRepository auctionRepository;

	private AuctionLiveService service;

	@BeforeEach
	void setUp() {
		service = new AuctionLiveService(cache, auctionRepository);
	}

	private Auction auction(Long currentPrice) {
		return Auction.builder()
				.id(1L)
				.artworkId(10L)
				.startPrice(10_000L)
				.currentPrice(currentPrice)
				.minBidUnit(1_000L)
				.previewStart(LocalDateTime.now().minusDays(2))
				.previewEnd(LocalDateTime.now().minusDays(1))
				.auctionEndAt(LocalDateTime.now().plusMinutes(5))
				.status(AuctionStatus.ONGOING)
				.build();
	}

	@Test
	void 캐시에_있으면_DB를_읽지_않는다() {
		AuctionLiveState cached = new AuctionLiveState(1L, 20_000L, 1_000L,
				LocalDateTime.now().plusMinutes(5), AuctionStatus.ONGOING, 99L);
		when(cache.find(1L)).thenReturn(Optional.of(cached));

		AuctionLiveView view = service.getView(1L, AuctionLiveReason.SNAPSHOT);

		assertThat(view.currentPrice()).isEqualTo(20_000L);
		assertThat(view.version()).isEqualTo(99L);
		assertThat(view.reason()).isEqualTo(AuctionLiveReason.SNAPSHOT);
		verifyNoInteractions(auctionRepository);
	}

	@Test
	void 캐시에_없으면_DB에서_읽어_스냅샷으로_채운다() {
		when(cache.find(1L)).thenReturn(Optional.empty());
		when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction(15_000L)));

		AuctionLiveView view = service.getView(1L, AuctionLiveReason.TICK);

		assertThat(view.currentPrice()).isEqualTo(15_000L);
		assertThat(view.remainingSeconds()).isPositive();
		ArgumentCaptor<AuctionLiveState> saved = ArgumentCaptor.forClass(AuctionLiveState.class);
		verify(cache).saveSnapshotIfAbsent(saved.capture());
		assertThat(saved.getValue().version()).isEqualTo(AuctionLiveState.SNAPSHOT_VERSION);
	}

	@Test
	void 경매가_없으면_IllegalArgumentException을_던진다() {
		when(cache.find(1L)).thenReturn(Optional.empty());
		when(auctionRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getView(1L, AuctionLiveReason.SNAPSHOT))
				.isInstanceOf(IllegalArgumentException.class);
		verify(cache, never()).saveSnapshotIfAbsent(any());
	}
}
