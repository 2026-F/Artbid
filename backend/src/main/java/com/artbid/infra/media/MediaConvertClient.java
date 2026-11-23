package com.artbid.infra.media;

import com.artbid.infra.storage.S3StorageProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.GetJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.Input;
import software.amazon.awssdk.services.mediaconvert.model.Job;
import software.amazon.awssdk.services.mediaconvert.model.JobSettings;
import software.amazon.awssdk.services.mediaconvert.model.ListJobsRequest;
import software.amazon.awssdk.services.mediaconvert.model.NotFoundException;
import software.amazon.awssdk.services.mediaconvert.model.Order;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaConvertClient {

	private static final int MAX_RECOVERY_PAGES = 5;

	private final MediaConvertProperties properties;
	private final S3StorageProperties s3StorageProperties;

	// 요청마다 SDK 클라이언트를 만들지 않도록 처음 쓸 때 한 번만 만든다 (enabled=false면 만들지 않음)
	private volatile software.amazon.awssdk.services.mediaconvert.MediaConvertClient sdkClient;

	public boolean isEnabled() {
		return properties.isEnabled();
	}

	/**
	 * 원본 동영상(sourceObjectKey)을 스트리밍용 포맷으로 트랜스코딩하는 MediaConvert job을 요청한다.
	 *
	 * 출력 포맷(코덱/해상도/HLS 등)과 출력 위치는 자바 코드에 하드코딩하지 않고, AWS 콘솔에서 만든
	 * Job Template(media.mediaconvert.job-template-name)을 참조한다.
	 *
	 * @return 생성된 job ID. media.mediaconvert.enabled=false(기본값, 로컬 개발)면 AWS를 호출하지 않고 빈 값.
	 */
	public Optional<String> requestTranscode(String sourceObjectKey) {
		if (!properties.isEnabled()) {
			log.info("[MediaConvert:disabled] {} 트랜스코딩 요청을 건너뜁니다 (media.mediaconvert.enabled=false)",
					sourceObjectKey);
			return Optional.empty();
		}

		String roleArn = requireConfigured(properties.getRoleArn(), "media.mediaconvert.role-arn");
		String jobTemplateName = requireConfigured(properties.getJobTemplateName(),
				"media.mediaconvert.job-template-name");
		String inputUri = inputUri(sourceObjectKey);

		CreateJobRequest.Builder requestBuilder = CreateJobRequest.builder()
				.role(roleArn)
				.jobTemplate(jobTemplateName)
				.clientRequestToken(requestToken(inputUri))
				.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(20)))
				// 콘솔/이벤트에서 어떤 원본의 job인지 바로 알 수 있도록 태깅
				.userMetadata(Map.of("objectKey", sourceObjectKey))
				.settings(JobSettings.builder()
						.inputs(Input.builder().fileInput(inputUri).build())
						.build());

		if (properties.getQueueArn() != null && !properties.getQueueArn().isBlank()) {
			requestBuilder.queue(properties.getQueueArn());
		}

		Job job = client().createJob(requestBuilder.build()).job();
		if (job == null || job.id() == null || job.id().isBlank()) {
			throw SdkClientException.create("MediaConvert 접수 응답에 작업 ID가 없습니다.");
		}
		String jobId = job.id();
		log.info("[MediaConvert] {} 트랜스코딩 job 요청 완료 (jobId={})", sourceObjectKey, jobId);
		return Optional.of(jobId);
	}

	/**
	 * 작업 이력에서 같은 토큰을 찾는다. 첫 요청 시각까지 조회한 경우에만 빈 결과를 반환한다.
	 */
	public Optional<String> findSubmittedJob(String sourceObjectKey, Instant firstAttemptAt) {
		String token = requestToken(inputUri(sourceObjectKey));
		Instant cutoff = firstAttemptAt.minusSeconds(60); // DB 서버와 AWS의 시계 차이를 고려한다.
		String nextToken = null;
		for (int page = 0; page < MAX_RECOVERY_PAGES; page++) {
			var response = client().listJobs(ListJobsRequest.builder()
					.order(Order.DESCENDING)
					.maxResults(20)
					.nextToken(nextToken)
					.overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(4)))
					.build());
			for (Job job : response.jobs()) {
				if (token.equals(job.clientRequestToken())) {
					return Optional.of(job.id());
				}
				if (job.createdAt() != null && job.createdAt().isBefore(cutoff)) {
					return Optional.empty();
				}
			}
			nextToken = response.nextToken();
			if (nextToken == null || nextToken.isBlank()) {
				return Optional.empty();
			}
		}
		throw new IllegalStateException("MediaConvert 작업 이력이 첫 요청 시각까지 확인되지 않았습니다.");
	}

	private String inputUri(String sourceObjectKey) {
		return "s3://%s/%s".formatted(s3StorageProperties.getBucket(), sourceObjectKey);
	}

	private String requestToken(String inputUri) {
		return UUID.nameUUIDFromBytes(inputUri.getBytes(StandardCharsets.UTF_8)).toString();
	}

	/**
	 * job 진행 상태를 조회한다. (EventBridge 알림 대신 폴링 방식 — TranscodeStatusPoller 참고)
	 */
	public TranscodeJobResult getJobResult(String jobId) {
		Job job;
		try {
			job = client().getJob(GetJobRequest.builder().id(jobId).build()).job();
		} catch (NotFoundException e) {
			return TranscodeJobResult.failed("MediaConvert job을 찾을 수 없습니다: " + jobId);
		}

		return switch (job.status()) {
			case COMPLETE -> TranscodeJobResult.complete();
			case ERROR -> TranscodeJobResult.failed(
					"MediaConvert job 실패 (code=%s): %s".formatted(job.errorCode(), job.errorMessage()));
			case CANCELED -> TranscodeJobResult.failed("MediaConvert job이 취소되었습니다");
			default -> TranscodeJobResult.inProgress(); // SUBMITTED, PROGRESSING
		};
	}

	@PreDestroy
	void close() {
		if (sdkClient != null) {
			sdkClient.close();
		}
	}

	private software.amazon.awssdk.services.mediaconvert.MediaConvertClient client() {
		if (sdkClient == null) {
			synchronized (this) {
				if (sdkClient == null) {
					String endpoint = requireConfigured(properties.getEndpoint(), "media.mediaconvert.endpoint");
					sdkClient = software.amazon.awssdk.services.mediaconvert.MediaConvertClient.builder()
							.region(Region.of(s3StorageProperties.getRegion()))
							.endpointOverride(URI.create(endpoint))
							.build();
				}
			}
		}
		return sdkClient;
	}

	private String requireConfigured(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(
					"media.mediaconvert.enabled=true인데 %s 설정이 비어 있습니다.".formatted(propertyName));
		}
		return value;
	}
}
