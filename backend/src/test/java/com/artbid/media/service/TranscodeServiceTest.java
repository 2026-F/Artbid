package com.artbid.media.service;

import com.artbid.infra.media.MediaConvertClient;
import com.artbid.infra.media.MediaConvertProperties;
import com.artbid.infra.media.TranscodeJobResult;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.infra.storage.S3StorageProperties;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.MediaType;
import com.artbid.media.domain.TranscodeStatus;
import com.artbid.media.repository.ArtworkMediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TranscodeServiceTest {

	private static final String SOURCE_KEY = "artworks/1/media/abc.mp4";
	private static final String SOURCE_URL = "http://storage/artbid-media/" + SOURCE_KEY;
	private static final String OUTPUT_URL = "http://storage/artbid-media/transcoded/abc.m3u8";

	private MediaConvertClient mediaConvertClient;
	private ArtworkMediaRepository repository;
	private S3PresignedUrlProvider urlProvider;
	private S3Client s3Client;
	private TranscodeService service;

	@BeforeEach
	void setup() {
		mediaConvertClient = mock(MediaConvertClient.class);
		repository = mock(ArtworkMediaRepository.class);
		urlProvider = mock(S3PresignedUrlProvider.class);
		s3Client = mock(S3Client.class);
		when(urlProvider.issuePublicUrl("transcoded/abc.m3u8")).thenReturn(OUTPUT_URL);

		service = new TranscodeService(mediaConvertClient, new MediaConvertProperties(), repository, urlProvider,
				new S3StorageProperties(), s3Client);
	}

	private ArtworkMedia video() {
		return new ArtworkMedia(1L, MediaType.VIDEO, null, SOURCE_URL, 0);
	}

	private ArtworkMedia processingVideo() {
		ArtworkMedia media = video();
		media.startTranscode("job-1");
		when(repository.findTop50ByTranscodeStatusOrderByIdAsc(TranscodeStatus.PROCESSING)).thenReturn(List.of(media));
		return media;
	}

	@Test
	void MediaConvert가_비활성화면_원본으로_건너뛴다() {
		when(mediaConvertClient.requestTranscode(SOURCE_KEY)).thenReturn(Optional.empty());
		ArtworkMedia media = video();

		service.start(media, SOURCE_KEY);

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.SKIPPED);
		assertThat(media.getUrl()).isEqualTo(SOURCE_URL);
	}

	@Test
	void job을_요청하면_진행_중_상태가_된다() {
		when(mediaConvertClient.requestTranscode(SOURCE_KEY)).thenReturn(Optional.of("job-1"));
		ArtworkMedia media = video();

		service.start(media, SOURCE_KEY);

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.PROCESSING);
		assertThat(media.getTranscodeJobId()).isEqualTo("job-1");
		assertThat(media.getUrl()).isNull();
	}

	@Test
	void 요청이_실패해도_예외를_던지지_않고_실패로_남긴다() {
		when(mediaConvertClient.requestTranscode(SOURCE_KEY)).thenThrow(new IllegalStateException("role-arn 없음"));
		ArtworkMedia media = video();

		service.start(media, SOURCE_KEY);

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.FAILED);
		assertThat(media.getTranscodeFailureReason()).contains("role-arn 없음");
	}

	@Test
	void job이_완료되고_출력이_있으면_출력_url로_완료한다() {
		ArtworkMedia media = processingVideo();
		when(mediaConvertClient.getJobResult("job-1")).thenReturn(TranscodeJobResult.complete());
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

		service.refreshProcessingJobs();

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.COMPLETED);
		assertThat(media.getUrl()).isEqualTo(OUTPUT_URL);
		verify(repository).save(media);
	}

	@Test
	void job은_완료됐지만_출력이_없으면_실패로_처리한다() {
		ArtworkMedia media = processingVideo();
		when(mediaConvertClient.getJobResult("job-1")).thenReturn(TranscodeJobResult.complete());
		when(s3Client.headObject(any(HeadObjectRequest.class)))
				.thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

		service.refreshProcessingJobs();

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.FAILED);
		assertThat(media.getUrl()).isEqualTo(SOURCE_URL);
	}

	@Test
	void job이_실패하면_실패로_처리한다() {
		ArtworkMedia media = processingVideo();
		when(mediaConvertClient.getJobResult("job-1")).thenReturn(TranscodeJobResult.failed("코덱 오류"));

		service.refreshProcessingJobs();

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.FAILED);
		assertThat(media.getTranscodeFailureReason()).isEqualTo("코덱 오류");
		verify(repository).save(media);
	}

	@Test
	void 아직_진행_중이거나_조회가_실패하면_상태를_바꾸지_않는다() {
		ArtworkMedia media = processingVideo();
		when(mediaConvertClient.getJobResult("job-1"))
				.thenReturn(TranscodeJobResult.inProgress())
				.thenThrow(new RuntimeException("일시적 네트워크 오류"));

		service.refreshProcessingJobs();
		service.refreshProcessingJobs();

		assertThat(media.getTranscodeStatus()).isEqualTo(TranscodeStatus.PROCESSING);
		verify(repository, never()).save(any());
	}

	@Test
	void 출력_파일_경로는_입력_파일명과_설정한_접두사로_만든다() {
		assertThat(service.outputObjectKey(SOURCE_URL)).isEqualTo("transcoded/abc.m3u8");
		assertThat(service.outputObjectKey("http://storage/bucket/no-extension")).isEqualTo("transcoded/no-extension.m3u8");
	}
}
