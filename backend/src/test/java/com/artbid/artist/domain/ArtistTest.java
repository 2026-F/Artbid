package com.artbid.artist.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistTest {

	@Test
	void 이름이_비어_있으면_등록할_수_없다() {
		assertThatThrownBy(() -> new Artist(" ", null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 부분_수정시_null인_필드는_기존_값을_유지한다() {
		Artist artist = new Artist("김작가", "소개", 1980, "대한민국", "https://img/profile.png");

		artist.update(null, "새 소개", null, null, null);

		assertThat(artist.getName()).isEqualTo("김작가");
		assertThat(artist.getBiography()).isEqualTo("새 소개");
		assertThat(artist.getBirthYear()).isEqualTo(1980);
		assertThat(artist.getNationality()).isEqualTo("대한민국");
	}

	@Test
	void 이름을_공백으로_수정할_수_없다() {
		Artist artist = new Artist("김작가", null, null, null, null);

		assertThatThrownBy(() -> artist.update("  ", null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(artist.getName()).isEqualTo("김작가");
	}
}
