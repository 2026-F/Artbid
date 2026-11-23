package com.artbid.infra.media;

import com.artbid.infra.storage.S3StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobResponse;
import software.amazon.awssdk.services.mediaconvert.model.Job;
import software.amazon.awssdk.services.mediaconvert.model.ListJobsRequest;
import software.amazon.awssdk.services.mediaconvert.model.ListJobsResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaConvertClientTest {

	private static final String OBJECT_KEY = "artworks/42/media/video.mp4";
	private static final Instant FIRST_ATTEMPT = Instant.parse("2026-11-21T03:00:00Z");

	private software.amazon.awssdk.services.mediaconvert.MediaConvertClient sdk;
	private MediaConvertClient client;

	@BeforeEach
	void setUp() {
		var properties = new MediaConvertProperties();
		properties.setEnabled(true);
		properties.setRoleArn("test-role");
		properties.setJobTemplateName("test-template");
		var storage = new S3StorageProperties();
		storage.setBucket("test-bucket");
		sdk = mock(software.amazon.awssdk.services.mediaconvert.MediaConvertClient.class);
		client = new MediaConvertClient(properties, storage);
		ReflectionTestUtils.setField(client, "sdkClient", sdk);
	}

	@Test
	void 접수에_사용한_토큰과_같은_토큰을_다음_페이지에서_찾는다() {
		when(sdk.createJob(any(CreateJobRequest.class))).thenReturn(
				CreateJobResponse.builder().job(Job.builder().id("accepted-job").build()).build());
		assertThat(client.requestTranscode(OBJECT_KEY)).contains("accepted-job");
		var createRequest = ArgumentCaptor.forClass(CreateJobRequest.class);
		verify(sdk).createJob(createRequest.capture());
		String token = createRequest.getValue().clientRequestToken();

		when(sdk.listJobs(any(ListJobsRequest.class))).thenAnswer(call -> {
			ListJobsRequest request = call.getArgument(0);
			if (request.nextToken() == null) {
				return ListJobsResponse.builder().nextToken("second-page")
						.jobs(Job.builder().id("other-job").createdAt(FIRST_ATTEMPT.plusSeconds(1)).build()).build();
			}
			assertThat(request.nextToken()).isEqualTo("second-page");
			return ListJobsResponse.builder().jobs(Job.builder().id("accepted-job")
					.clientRequestToken(token).createdAt(FIRST_ATTEMPT).build()).build();
		});

		assertThat(client.findSubmittedJob(OBJECT_KEY, FIRST_ATTEMPT)).contains("accepted-job");
		verify(sdk, times(2)).listJobs(any(ListJobsRequest.class));
	}

	@Test
	void 첫_시도_이전까지_검색하면_미접수를_반환한다() {
		when(sdk.listJobs(any(ListJobsRequest.class))).thenReturn(ListJobsResponse.builder()
				.nextToken("older-page").jobs(Job.builder().id("older-job")
						.createdAt(FIRST_ATTEMPT.minusSeconds(61)).build()).build());

		assertThat(client.findSubmittedJob(OBJECT_KEY, FIRST_ATTEMPT)).isEmpty();
		verify(sdk).listJobs(any(ListJobsRequest.class));
	}

	@Test
	void 페이지_제한으로_검색이_끝나지_않으면_미접수로_판단하지_않는다() {
		when(sdk.listJobs(any(ListJobsRequest.class))).thenReturn(ListJobsResponse.builder().nextToken("next")
				.jobs(Job.builder().id("other-job").createdAt(FIRST_ATTEMPT).build()).build());

		assertThatThrownBy(() -> client.findSubmittedJob(OBJECT_KEY, FIRST_ATTEMPT))
				.isInstanceOf(IllegalStateException.class);
		verify(sdk, times(5)).listJobs(any(ListJobsRequest.class));
	}
}
