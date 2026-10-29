package com.artbid.media.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtworkMediaTest {

	private static final String SOURCE_URL = "http://storage/artbid-media/artworks/1/media/abc.mp4";

	private ArtworkMedia video() {
		return new ArtworkMedia(1L, MediaType.VIDEO, null, SOURCE_URL, 0);
	}

	@Test
	void 트랜스코딩을_건너뛰면_원본을_감상용_url로_쓴다() {
		ArtworkMedia media = video();

		media.skipTranscode();

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.SKIPPED);
		assertThat(media.getUrl()).isEqualTo(SOURCE_URL);
	}

	@Test
	void 진행_중인_트랜스코딩이_완료되면_출력_url이_채워진다() {
		ArtworkMedia media = video();
		media.startTranscode("job-1");
		assertThat(media.getUrl()).isNull();

		media.completeTranscode("http://storage/artbid-media/transcoded/abc.m3u8");

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.COMPLETED);
		assertThat(media.getUrl()).endsWith("abc.m3u8");
	}

	@Test
	void 진행_중이_아니면_완료_처리할_수_없다() {
		ArtworkMedia media = video();

		assertThatThrownBy(() -> media.completeTranscode("http://x/abc.m3u8"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 실패하면_원본으로_대체하고_사유를_남긴다() {
		ArtworkMedia media = video();
		media.startTranscode("job-1");

		media.failTranscode("x".repeat(1000));

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.FAILED);
		assertThat(media.getUrl()).isEqualTo(SOURCE_URL);
		assertThat(media.getTranscodeFailureReason()).hasSize(500);
	}
}
