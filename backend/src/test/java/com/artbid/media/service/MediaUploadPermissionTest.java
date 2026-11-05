package com.artbid.media.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.exception.MediaAccessDeniedException;
import com.artbid.media.repository.ArtworkMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MediaUploadPermissionTest {

	private ArtworkMediaRepository mediaRepository;
	private S3PresignedUrlProvider urlProvider;
	private MediaService service;

	@BeforeEach
	void setUp() {
		mediaRepository = mock(ArtworkMediaRepository.class);
		ArtworkRepository artworks = mock(ArtworkRepository.class);
		urlProvider = mock(S3PresignedUrlProvider.class);
		service = new MediaService(mediaRepository, artworks, urlProvider, mock(TranscodeService.class));

		Artwork artwork = Artwork.builder().consignorId(1L).artistId(2L).title("무제")
				.category(ArtworkCategory.PAINTING).startPrice(100_000L).build();
		ReflectionTestUtils.setField(artwork, "id", 10L);
		when(artworks.findById(10L)).thenReturn(Optional.of(artwork));
		when(urlProvider.issueUploadUrl(anyString(), anyString())).thenReturn("https://upload.example");
		when(urlProvider.issuePublicUrl(anyString())).thenReturn("https://media.example/photo.jpg");
		when(mediaRepository.findByArtworkIdOrderBySortOrder(10L)).thenReturn(List.of());
	}

	@Test
	void 위탁자는_업로드_URL을_받는다() {
		var response = service.issuePresignedUrl(10L, 1L, false, "photo.jpg", "image/jpeg");

		assertThat(response.objectKey()).startsWith("artworks/10/media/");
	}

	@Test
	void 다른_회원은_업로드_URL을_받을_수_없다() {
		assertThatThrownBy(() -> service.issuePresignedUrl(10L, 5L, false, "photo.jpg", "image/jpeg"))
				.isInstanceOf(MediaAccessDeniedException.class);
		verifyNoInteractions(urlProvider);
	}

	@Test
	void 관리자는_업로드_URL을_받는다() {
		assertThat(service.issuePresignedUrl(10L, 99L, true, "photo.jpg", "image/jpeg").objectKey())
				.startsWith("artworks/10/media/");
	}

	@Test
	void 다른_회원은_업로드_완료를_등록할_수_없다() {
		assertThatThrownBy(() -> service.completeUpload(10L, 5L, false, "artworks/10/media/a.jpg", "PHOTO"))
				.isInstanceOf(MediaAccessDeniedException.class);
		verify(mediaRepository, never()).save(any());
	}

	@Test
	void 다른_작품_경로의_객체는_등록할_수_없다() {
		assertThatThrownBy(() -> service.completeUpload(10L, 1L, false, "artworks/11/media/a.jpg", "PHOTO"))
				.isInstanceOf(MediaAccessDeniedException.class);
		assertThatThrownBy(() -> service.completeUpload(10L, 1L, false, "artworks/10/media/../../11/a.jpg", "PHOTO"))
				.isInstanceOf(MediaAccessDeniedException.class);
		verify(mediaRepository, never()).save(any());
	}

	@Test
	void 위탁자는_자기_작품_경로의_업로드를_완료한다() {
		service.completeUpload(10L, 1L, false, "artworks/10/media/a.jpg", "PHOTO");

		verify(mediaRepository).save(any(ArtworkMedia.class));
	}
}
