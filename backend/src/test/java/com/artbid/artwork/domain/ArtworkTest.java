package com.artbid.artwork.domain;

import com.artbid.artwork.exception.ArtworkNotEditableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtworkTest {

	private Artwork artwork() {
		return Artwork.builder()
				.consignorId(1L)
				.artistId(2L)
				.title("무제")
				.category(ArtworkCategory.PAINTING)
				.startPrice(1_000_000L)
				.estimatedPrice(2_000_000L)
				.build();
	}

	private Artwork.Changes changes(String title, Long startPrice) {
		return new Artwork.Changes(null, title, null, null, null, null, null, null, null, startPrice, null, null,
				null);
	}

	@Test
	void 등록하면_심사_대기_상태가_된다() {
		assertThat(artwork().getStatus()).isEqualTo(ArtworkStatus.PENDING_REVIEW);
	}

	@Test
	void 제목이_비어_있으면_등록할_수_없다() {
		assertThatThrownBy(() -> Artwork.builder().consignorId(1L).artistId(2L).title(" ")
				.category(ArtworkCategory.PAINTING).startPrice(1000L).build())
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 시작가는_0보다_커야_한다() {
		assertThatThrownBy(() -> Artwork.builder().consignorId(1L).artistId(2L).title("무제")
				.category(ArtworkCategory.PAINTING).startPrice(0L).build())
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 부분_수정시_null인_필드는_기존_값을_유지한다() {
		Artwork artwork = artwork();

		artwork.update(changes("새 제목", null));

		assertThat(artwork.getTitle()).isEqualTo("새 제목");
		assertThat(artwork.getStartPrice()).isEqualTo(1_000_000L);
		assertThat(artwork.getArtistId()).isEqualTo(2L);
	}

	@Test
	void 반려된_작품은_수정할_수_있다() {
		Artwork artwork = artwork();
		ReflectionTestUtils.setField(artwork, "status", ArtworkStatus.REJECTED);

		artwork.update(changes(null, 500_000L));

		assertThat(artwork.getStartPrice()).isEqualTo(500_000L);
	}

	@Test
	void 프리뷰_이후_상태에서는_수정할_수_없다() {
		for (ArtworkStatus status : new ArtworkStatus[] {ArtworkStatus.PREVIEW, ArtworkStatus.IN_AUCTION,
				ArtworkStatus.SOLD}) {
			Artwork artwork = artwork();
			ReflectionTestUtils.setField(artwork, "status", status);

			assertThatThrownBy(() -> artwork.update(changes("새 제목", null)))
					.isInstanceOf(ArtworkNotEditableException.class);
			assertThat(artwork.getTitle()).isEqualTo("무제");
		}
	}
}
