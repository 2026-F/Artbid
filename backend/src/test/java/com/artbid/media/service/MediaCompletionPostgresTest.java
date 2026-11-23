package com.artbid.media.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.infra.media.MediaConvertClient;
import com.artbid.infra.media.TranscodeJobResult;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.media.controller.MediaController;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import software.amazon.awssdk.core.exception.SdkClientException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class MediaCompletionPostgresTest {
	@Autowired MediaService service;
	@Autowired ArtworkRepository artworks;
	@Autowired ArtworkMediaRepository media;
	@MockBean S3PresignedUrlProvider urls;
	@Autowired TranscodeDispatcher dispatcher;
	@Autowired PlatformTransactionManager transactionManager;
	@MockBean MediaConvertClient mediaConvert;
	@MockBean(name = "applicationTaskExecutor") TaskExecutor executor;
	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private Long artworkId;
	private String key;

	@BeforeEach
	void setup() {
		artworkId = artworks.save(Artwork.builder().consignorId(7L).artistId(2L)
				.category(ArtworkCategory.PAINTING).title("완료 재시도 검증").startPrice(100000L).build()).getId();
		when(mediaConvert.isEnabled()).thenReturn(true);
		captureTasks();
		key = "artworks/" + artworkId + "/media/test";
		when(urls.issuePublicUrl(anyString())).thenAnswer(call -> "https://media.example/" + call.getArgument(0));
	}

	private void captureTasks() {
		doAnswer(call -> {
			tasks.add(call.getArgument(0));
			return null;
		}).when(executor).execute(any(Runnable.class));
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
			verifyNoInteractions(mediaConvert);
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void 작품이_잠겨_있으면_타인은_즉시_거절되고_소유자는_타임아웃_후_재시도할_수_있다() throws Exception {
		var mvc = MockMvcBuilders.standaloneSetup(new MediaController(service))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
		String path = "/api/artworks/{artworkId}/media/complete";

		mvc.perform(post(path, artworkId).param("objectKey", key).param("mediaType", "PHOTO"))
				.andExpect(status().isUnauthorized());

		try (var requests = Executors.newVirtualThreadPerTaskExecutor()) {
			new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
				assertThat(artworks.findByIdForUpdate(artworkId)).isPresent();
				var unauthorized = requests.submit(() -> mvc.perform(post(path, artworkId)
								.principal(() -> "8").param("objectKey", key).param("mediaType", "PHOTO"))
						.andExpect(status().isForbidden()));
				var owner = requests.submit(() -> mvc.perform(post(path, artworkId)
								.principal(() -> "7").param("objectKey", key).param("mediaType", "PHOTO"))
						.andExpect(status().isServiceUnavailable()));
				try {
					unauthorized.get(2, TimeUnit.SECONDS);
					owner.get(7, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new AssertionError(e);
				} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
					throw new AssertionError(e);
				}
			});
		}

		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).isEmpty();
		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(post(path, artworkId).principal(() -> "7").param("objectKey", key)
					.param("mediaType", "PHOTO")).andExpect(status().isOk());
		}
		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).hasSize(1);
	}

	@ParameterizedTest
	@EnumSource(TranscodeStatus.class)
	void 영상_상태와_관계없이_원본_URL로_재등록을_방지한다(TranscodeStatus status) {
		ArtworkMedia video = new ArtworkMedia(artworkId, MediaType.VIDEO, null,
				urls.issuePublicUrl(key), 0);
		video.startTranscode("local-job");
		switch (status) {
			case COMPLETED -> video.completeTranscode("https://media.example/converted.m3u8");
			case FAILED -> video.failTranscode("검증용 실패");
			case SKIPPED -> video.skipTranscode();
			case PROCESSING -> { }
		}
		media.save(video);

		when(urls.issuePublicUrl(key)).thenReturn("https://new-media.example/" + key);
		service.completeUpload(artworkId, 7L, false, key, "VIDEO");

		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement().satisfies(registered -> {
			assertThat(registered.getId()).isEqualTo(video.getId());
			assertThat(registered.getObjectKey()).isEqualTo(key);
			assertThat(registered.getTranscodeStatus()).isEqualTo(status);
		});
		verifyNoInteractions(mediaConvert);
		assertThat(tasks).isEmpty();
	}

	@Test
	void 새_업로드는_공개_URL이_바뀌어도_같은_키로_한_건만_등록된다() {
		service.completeUpload(artworkId, 7L, false, key, "PHOTO");
		when(urls.issuePublicUrl(key)).thenReturn("https://new-media.example/" + key);

		service.completeUpload(artworkId, 7L, false, key, "PHOTO");
		assertThatThrownBy(() -> service.completeUpload(artworkId, 7L, false, key, "VIDEO"))
				.isInstanceOf(MediaTypeConflictException.class);

		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement().satisfies(photo -> {
			assertThat(photo.getObjectKey()).isEqualTo(key);
			assertThat(photo.getUrl()).isEqualTo("https://media.example/" + key);
		});
	}

	@Test
	void 기존_사진은_재시도할_때_키를_채우고_이전_스냅샷의_갱신에도_키를_유지한다() {
		ArtworkMedia oldPhoto = media.save(new ArtworkMedia(artworkId, MediaType.PHOTO,
				"https://old-media.example/bucket/" + key, null, 0));

		service.completeUpload(artworkId, 7L, false, key, "PHOTO");
		oldPhoto.updateUrl("https://redirected-media.example/" + key);
		media.save(oldPhoto);

		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement().satisfies(photo -> {
			assertThat(photo.getId()).isEqualTo(oldPhoto.getId());
			assertThat(photo.getObjectKey()).isEqualTo(key);
		});
	}

	@Test
	void DB가_같은_작품과_키의_중복_행을_거절한다() {
		media.saveAndFlush(new ArtworkMedia(artworkId, MediaType.PHOTO, "https://old.example/photo", null, 0, key));

		assertThatThrownBy(() -> media.saveAndFlush(
				new ArtworkMedia(artworkId, MediaType.PHOTO, "https://new.example/photo", null, 1, key)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_artwork_media_artwork_object_key");
	}

	@Test
	void 커밋_후에만_트랜잭션_없이_AWS에_요청하고_작업_ID를_저장한다() {
		when(mediaConvert.requestTranscode(key)).thenAnswer(call -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement()
					.satisfies(video -> assertThat(video.getTranscodeJobId()).isNull());
			dispatcher.retryPending();
			return Optional.of("local-job");
		});

		new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
			service.completeUpload(artworkId, 7L, false, key, "VIDEO");
			assertThat(tasks).isEmpty();
			verify(mediaConvert, never()).requestTranscode(anyString());
		});
		service.completeUpload(artworkId, 7L, false, key, "VIDEO");
		assertThat(tasks).hasSize(1);
		tasks.remove().run();

		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement()
				.satisfies(video -> assertThat(video.getTranscodeJobId()).isEqualTo("local-job"));
		verify(mediaConvert, times(1)).requestTranscode(key);
	}

	@Test
	void 롤백된_영상은_커밋_후_실행이나_폴링으로_요청하지_않는다() {
		new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
			service.completeUpload(artworkId, 7L, false, key, "VIDEO");
			transaction.setRollbackOnly();
		});

		dispatcher.retryPending();

		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).isEmpty();
		assertThat(tasks).isEmpty();
		verify(mediaConvert, never()).requestTranscode(anyString());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void 폴러가_미전송_기록을_실행기에_넘겨_요청을_복구한다(boolean expiredClaim) {
		doThrow(new TaskRejectedException("검증용 실행기 거절")).when(executor).execute(any(Runnable.class));
		when(mediaConvert.requestTranscode(key)).thenReturn(Optional.of("recovered-job"));
		service.completeUpload(artworkId, 7L, false, key, "VIDEO");
		captureTasks();
		ArtworkMedia pending = media.findByArtworkIdOrderBySortOrder(artworkId).getFirst();
		if (expiredClaim) {
			expireTranscodeClaim(pending);
			when(mediaConvert.findSubmittedJob(eq(key), any(Instant.class))).thenReturn(Optional.empty());
		}

		dispatcher.retryPending();
		dispatcher.retryPending();

		assertThat(tasks).hasSize(1);
		verify(mediaConvert, never()).requestTranscode(key);
		tasks.remove().run();

		assertThat(media.findById(pending.getId()).orElseThrow().getTranscodeJobId()).isEqualTo("recovered-job");
		verify(mediaConvert, times(1)).requestTranscode(key);
	}

	@Test
	void 접수_응답이_타임아웃되면_이력을_확인할_때까지_대기하고_기존_작업을_연결한다() {
		when(mediaConvert.requestTranscode(key)).thenThrow(SdkClientException.create("접수 응답 타임아웃"));
		service.completeUpload(artworkId, 7L, false, key, "VIDEO");
		tasks.remove().run();
		ArtworkMedia pending = media.findByArtworkIdOrderBySortOrder(artworkId).getFirst();
		assertThat(pending.getTranscodeStatus()).isEqualTo(TranscodeStatus.PROCESSING);
		assertThat(pending.getTranscodeJobId()).isNull();
		assertThat(pending.getTranscodeFirstAttemptAt()).isNotNull();

		dispatcher.retryPending();
		assertThat(tasks).isEmpty();
		expireTranscodeClaim(pending);
		when(mediaConvert.findSubmittedJob(eq(key), any(Instant.class)))
				.thenThrow(SdkClientException.create("작업 이력 조회 타임아웃"))
				.thenReturn(Optional.of("already-accepted-job"));

		dispatcher.retryPending();
		tasks.remove().run();
		ArtworkMedia stillPending = media.findById(pending.getId()).orElseThrow();
		assertThat(stillPending.getTranscodeStatus()).isEqualTo(TranscodeStatus.PROCESSING);
		assertThat(stillPending.getTranscodeJobId()).isNull();
		expireTranscodeClaim(stillPending);

		dispatcher.retryPending();
		tasks.remove().run();
		ArtworkMedia recovered = media.findById(pending.getId()).orElseThrow();
		assertThat(recovered.getTranscodeJobId()).isEqualTo("already-accepted-job");
		assertThat(recovered.getTranscodeFirstAttemptAt()).isNull();
		verify(mediaConvert, times(1)).requestTranscode(key);
	}

	@Test
	void 작업_결과_폴링도_실행기에_위임하고_중복_예약하지_않는다() {
		ArtworkMedia video = new ArtworkMedia(artworkId, MediaType.VIDEO, null, urls.issuePublicUrl(key), 0, key);
		video.startTranscode("processing-job");
		media.save(video);
		when(mediaConvert.getJobResult("processing-job"))
				.thenReturn(TranscodeJobResult.inProgress());

		dispatcher.scheduleStatusRefresh();
		dispatcher.scheduleStatusRefresh();

		assertThat(tasks).hasSize(1);
		verify(mediaConvert, never()).getJobResult(anyString());
		tasks.remove().run();
		verify(mediaConvert).getJobResult("processing-job");
	}

	private void expireTranscodeClaim(ArtworkMedia pending) {
		Instant elapsed = Instant.now().minusSeconds(180);
		pending.claimTranscode(elapsed, elapsed.plusSeconds(120));
		media.save(pending);
	}

	@Test
	void 변환이_꺼져_있으면_업로드_완료와_함께_원본을_재생_URL로_저장한다() {
		when(mediaConvert.isEnabled()).thenReturn(false);

		service.completeUpload(artworkId, 7L, false, key, "VIDEO");

		assertThat(media.findByArtworkIdOrderBySortOrder(artworkId)).singleElement().satisfies(video -> {
			assertThat(video.getTranscodeStatus()).isEqualTo(TranscodeStatus.SKIPPED);
			assertThat(video.getUrl()).isEqualTo(video.getSourceUrl());
		});
		assertThat(tasks).isEmpty();
		verify(mediaConvert, never()).requestTranscode(anyString());
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
		verifyNoInteractions(mediaConvert);
	}
}
