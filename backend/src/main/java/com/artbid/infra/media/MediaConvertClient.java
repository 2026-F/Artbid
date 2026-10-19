package com.artbid.infra.media;

import com.artbid.infra.storage.S3StorageProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.GetJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.Input;
import software.amazon.awssdk.services.mediaconvert.model.Job;
import software.amazon.awssdk.services.mediaconvert.model.JobSettings;
import software.amazon.awssdk.services.mediaconvert.model.NotFoundException;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaConvertClient {

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
		String inputUri = "s3://%s/%s".formatted(s3StorageProperties.getBucket(), sourceObjectKey);

		CreateJobRequest.Builder requestBuilder = CreateJobRequest.builder()
				.role(roleArn)
				.jobTemplate(jobTemplateName)
				// 콘솔/이벤트에서 어떤 원본의 job인지 바로 알 수 있도록 태깅
				.userMetadata(Map.of("objectKey", sourceObjectKey))
				.settings(JobSettings.builder()
						.inputs(Input.builder().fileInput(inputUri).build())
						.build());

		if (properties.getQueueArn() != null && !properties.getQueueArn().isBlank()) {
			requestBuilder.queue(properties.getQueueArn());
		}

		String jobId = client().createJob(requestBuilder.build()).job().id();
		log.info("[MediaConvert] {} 트랜스코딩 job 요청 완료 (jobId={})", sourceObjectKey, jobId);
		return Optional.of(jobId);
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
