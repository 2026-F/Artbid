package com.artbid.artwork.service;

import com.artbid.artist.repository.ArtistRepository;
import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.dto.ArtworkUpdateRequest;
import com.artbid.artwork.exception.ArtworkForbiddenException;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.media.repository.ArtworkMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtworkOwnershipTest {

	private static final ArtworkUpdateRequest RENAME = new ArtworkUpdateRequest(null, "새 제목", null, null, null,
			null, null, null, null, null, null, null, null);

	private ArtworkRepository artworks;
	private ArtworkService service;
	private Artwork artwork;

	@BeforeEach
	void setUp() {
		artworks = mock(ArtworkRepository.class);
		service = new ArtworkService(artworks, mock(ArtistRepository.class), mock(ArtworkMediaRepository.class),
				mock(AuctionRepository.class));
		artwork = Artwork.builder().consignorId(1L).artistId(2L).title("무제")
				.category(ArtworkCategory.PAINTING).startPrice(100_000L).build();
		ReflectionTestUtils.setField(artwork, "id", 10L);
		when(artworks.findById(10L)).thenReturn(Optional.of(artwork));
	}

	@Test
	void 위탁자_본인은_작품을_수정할_수_있다() {
		assertThat(service.update(10L, 1L, false, RENAME).title()).isEqualTo("새 제목");
	}

	@Test
	void 다른_회원은_작품을_수정할_수_없다() {
		assertThatThrownBy(() -> service.update(10L, 5L, false, RENAME))
				.isInstanceOf(ArtworkForbiddenException.class);
		assertThat(artwork.getTitle()).isEqualTo("무제");
	}

	@Test
	void 관리자는_남의_작품도_수정할_수_있다() {
		assertThat(service.update(10L, 99L, true, RENAME).title()).isEqualTo("새 제목");
	}
}
