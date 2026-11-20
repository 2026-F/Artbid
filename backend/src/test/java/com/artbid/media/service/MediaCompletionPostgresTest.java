package com.artbid.media.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.MediaType;
import com.artbid.media.domain.TranscodeStatus;
import com.artbid.media.exception.MediaAccessDeniedException;
import com.artbid.media.exception.MediaTypeConflictException;
import com.artbid.media.repository.ArtworkMediaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class MediaCompletionPostgresTest {
	@Autowired MediaService service;
	@Autowired ArtworkRepository artworks;
	@Autowired ArtworkMediaRepository media;
	@MockBean S3PresignedUrlProvider urls;
	@MockBean TranscodeService transcode;
	private Long artworkId;
	private String key;

	@BeforeEach
	void setup() {
		artworkId = artworks.save(Artwork.builder().consignorId(7L).artistId(2L)
				.category(ArtworkCategory.PAINTING).title("완료 재시도 검증").startPrice(100000L).build()).getId();
		key = "artworks/" + artworkId + "/media/test";
		when(urls.issuePublicUrl(anyString())).thenAnswer(call -> "https://media.example/" + call.getArgument(0));
	}

	@AfterEach
	void cleanup() {
		media.deleteAll(media.findByArtworkIdOrderBySortOrder(artworkId));
		artworks.deleteById(artworkId);
	}

	@Test
	void 같은_객체와_다른_객체의_동시_완료가_중복과_정렬_충돌을_만들지_않는다() throws Exception {
		var executor = Executors.newFixedThreadPool(8);
		CountDownLatch ready = new CountDownLatch(8);
		CountDownLatch start = new CountDownLatch(1);
		try {
			var calls = IntStream.range(0, 8).mapToObj(index -> executor.submit(() -> {
				ready.countDown();
				if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 시간 초과");
				service.completeUpload(artworkId, 7L, false, key + (index < 6 ? "" : index), "PHOTO");
				return true;
			})).toList();
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (var call : calls) assertThat(call.get(20, TimeUnit.SECONDS)).isTrue();
			List<ArtworkMedia> registered = media.findByArtworkIdOrderBySortOrder(artworkId);
			assertThat(registered).hasSize(3);
			assertThat(registered).extracting(ArtworkMedia::getSortOrder).containsExactly(0, 1, 2);
			service.completeUpload(artworkId, 7L, false, key, "PHOTO");
			assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).hasSize(3);
			verifyNoInteractions(transcode);
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	@ParameterizedTest
	@EnumSource(TranscodeStatus.class)
	void 영상_상태와_관계없이_원본_URL로_재등록을_방지한다(TranscodeStatus status) {
		doAnswer(call -> {
			ArtworkMedia video = call.getArgument(0);
			video.startTranscode("local-job");
			switch (status) {
				case COMPLETED -> video.completeTranscode("https://media.example/converted.m3u8");
				case FAILED -> video.failTranscode("검증용 실패");
				case SKIPPED -> video.skipTranscode();
				case PROCESSING -> { }
			}
			return null;
		}).when(transcode).start(any(), eq(key));
		service.completeUpload(artworkId, 7L, false, key, "VIDEO");
		service.completeUpload(artworkId, 7L, false, key, "VIDEO");
		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement()
				.satisfies(video -> assertThat(video.getTranscodeStatus()).isEqualTo(status));
		verify(transcode, times(1)).start(any(), eq(key));
	}

	@Test
	void 기존_객체_유형_변경과_타인_재시도는_거절한다() {
		service.completeUpload(artworkId, 7L, false, key, "PHOTO");
		assertThatThrownBy(() -> service.completeUpload(artworkId, 7L, false, key, "VIDEO"))
				.isInstanceOf(MediaTypeConflictException.class);
		assertThatThrownBy(() -> service.completeUpload(artworkId, 8L, false, key, "PHOTO"))
				.isInstanceOf(MediaAccessDeniedException.class);
		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement()
				.satisfies(photo -> assertThat(photo.getMediaType()).isEqualTo(MediaType.PHOTO));
		verifyNoInteractions(transcode);
	}
}
