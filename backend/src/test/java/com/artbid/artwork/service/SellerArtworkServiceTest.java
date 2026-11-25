package com.artbid.artwork.service;

import com.artbid.artist.repository.ArtistRepository;
import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.dto.ArtworkPageResponse;
import com.artbid.artwork.exception.ArtworkNotFoundException;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.media.repository.ArtworkMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SellerArtworkServiceTest {
	private final ArtworkRepository artworks = mock(ArtworkRepository.class);
	private final ArtistRepository artists = mock(ArtistRepository.class);
	private final ArtworkMediaRepository media = mock(ArtworkMediaRepository.class);
	private final AuctionRepository auctions = mock(AuctionRepository.class);
	private ArtworkService service;

	@BeforeEach
	void setup() {
		service = new ArtworkService(artworks, artists, media, auctions);
	}

	private Artwork artwork() {
		Artwork artwork = Artwork.builder().consignorId(7L).artistId(2L).category(ArtworkCategory.PAINTING)
				.title("심사 대기 작품").startPrice(100000L).build();
		ReflectionTestUtils.setField(artwork, "id", 42L);
		return artwork;
	}

	@Test
	void 본인_목록은_소유자_조건으로_조회하고_페이지_정보를_보존한다() {
		PageRequest pageable = PageRequest.of(1, 20);
		when(artworks.findByConsignorId(7L, pageable)).thenReturn(new PageImpl<>(List.of(artwork()), pageable, 21));
		when(media.findByArtworkIdInOrderBySortOrderAscIdAsc(any())).thenReturn(List.of());
		when(auctions.findByArtworkIdIn(any())).thenReturn(List.of());
		ArtworkPageResponse response = service.getMyArtworks(7L, pageable);
		assertThat(response.totalElements()).isEqualTo(21);
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.content()).singleElement().satisfies(item -> {
			assertThat(item.id()).isEqualTo(42L);
			assertThat(item.status()).isEqualTo(artwork().getStatus());
		});
		verify(artworks).findByConsignorId(7L, pageable);
	}

	@Test
	void 본인_비공개_작품은_기존_상세_형식으로_조회한다() {
		when(artworks.findByIdAndConsignorId(42L, 7L)).thenReturn(Optional.of(artwork()));
		when(media.findByArtworkIdOrderBySortOrder(42L)).thenReturn(List.of());
		when(auctions.findByArtworkIdIn(any())).thenReturn(List.of());
		assertThat(service.getMyArtwork(42L, 7L).consignorId()).isEqualTo(7L);
	}

	@Test
	void 타인_작품은_상세_미디어를_읽기_전에_거절한다() {
		when(artworks.findByIdAndConsignorId(42L, 8L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getMyArtwork(42L, 8L)).isInstanceOf(ArtworkNotFoundException.class);
		verifyNoInteractions(artists, media, auctions);
	}

	@Test
	void 없는_작품은_찾을_수_없음_예외를_반환한다() {
		when(artworks.findByIdAndConsignorId(99L, 7L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getMyArtwork(99L, 7L)).isInstanceOf(ArtworkNotFoundException.class);
	}
}
