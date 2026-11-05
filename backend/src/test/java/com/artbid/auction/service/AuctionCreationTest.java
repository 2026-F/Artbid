package com.artbid.auction.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.common.exception.AuctionCreationConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class AuctionCreationTest {

	private static final LocalDateTime NOW = LocalDateTime.now();

	private AuctionRepository auctions;
	private AuctionService service;
	private Artwork artwork;

	@BeforeEach
	void setUp() {
		auctions = mock(AuctionRepository.class);
		ArtworkRepository artworks = mock(ArtworkRepository.class);
		service = new AuctionService(auctions, artworks);
		artwork = Artwork.builder().consignorId(1L).artistId(2L).title("무제")
				.category(ArtworkCategory.PAINTING).startPrice(100_000L).build();
		ReflectionTestUtils.setField(artwork, "id", 10L);
		when(artworks.findById(10L)).thenReturn(Optional.of(artwork));
		when(auctions.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private void create() {
		service.createAuction(10L, 100_000L, 10_000L, NOW.plusHours(1), NOW.plusHours(2), NOW.plusDays(1));
	}

	@Test
	void 승인된_작품에_경매를_연다() {
		artwork.approve();
		when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of());

		create();

		verify(auctions).save(any(Auction.class));
	}

	@Test
	void 심사_전_작품에는_경매를_열_수_없다() {
		assertThatThrownBy(this::create).isInstanceOf(AuctionCreationConflictException.class);
		verify(auctions, never()).save(any());
	}

	@Test
	void 마감되지_않은_경매가_있으면_새로_열_수_없다() {
		artwork.approve();
		Auction open = mock(Auction.class);
		when(open.getStatus()).thenReturn(AuctionStatus.PREVIEW);
		when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of(open));

		assertThatThrownBy(this::create).isInstanceOf(AuctionCreationConflictException.class);
		verify(auctions, never()).save(any());
	}

	@Test
	void 이전_경매가_마감됐으면_다시_열_수_있다() {
		artwork.approve();
		Auction closed = mock(Auction.class);
		when(closed.getStatus()).thenReturn(AuctionStatus.CLOSED);
		when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of(closed));

		create();

		verify(auctions).save(any(Auction.class));
	}

	@Test
	void 마감된_경매의_상태는_되돌릴_수_없다() {
		Auction closed = mock(Auction.class);
		when(closed.getStatus()).thenReturn(AuctionStatus.CLOSED);
		when(auctions.findById(5L)).thenReturn(Optional.of(closed));

		assertThatThrownBy(() -> service.updateStatus(5L, AuctionStatus.ONGOING))
				.isInstanceOf(AuctionCreationConflictException.class);
		verify(closed, never()).overrideStatus(any());
	}
}
