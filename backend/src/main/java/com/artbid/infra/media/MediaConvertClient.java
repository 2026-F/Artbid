package com.artbid.infra.media;

import com.artbid.infra.storage.S3StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.Input;
import software.amazon.awssdk.services.mediaconvert.model.JobSettings;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaConvertClient {

	private final MediaConvertProperties properties;
	private final S3StorageProperties s3StorageProperties;

	/**
	 * 원본 동영상(sourceObjectKey)을 스트리밍용 포맷으로 트랜스코딩하는 MediaConvert job을 요청한다.
	 *
	 * 출력 포맷(코덱/해상도/HLS 등)은 자바 코드에 하드코딩하지 않고, AWS 콘솔에서 만든
	 * Job Template(media.mediaconvert.job-template-name)을 참조하는 구조로 설계했다 —
	 * 스트리밍 트랙과 협의해서 콘솔에서 템플릿을 만들고 이름만 설정에 채우면 된다.
	 *
	 * media.mediaconvert.enabled=false(기본값, 로컬 개발)면 실제 AWS 호출 없이 로그만 남긴다.
	 * job 완료를 감지해서 ArtworkMedia.url을 채우는 부분(EventBridge/폴링 등)은 아직 TODO —
	 * 스트리밍 트랙 인프라(infra/streaming)와 함께 후속으로 설계할 예정.
	 */
	public void requestTranscode(String sourceObjectKey, String outputPrefix) {
		if (!properties.isEnabled()) {
			log.info("[MediaConvert:disabled] {} 트랜스코딩 요청을 건너뜁니다 (media.mediaconvert.enabled=false)",
					sourceObjectKey);
			return;
		}

		String endpoint = requireConfigured(properties.getEndpoint(), "media.mediaconvert.endpoint");
		String roleArn = requireConfigured(properties.getRoleArn(), "media.mediaconvert.role-arn");
		String jobTemplateName = requireConfigured(properties.getJobTemplateName(),
				"media.mediaconvert.job-template-name");

		String inputUri = "s3://%s/%s".formatted(s3StorageProperties.getBucket(), sourceObjectKey);

		try (software.amazon.awssdk.services.mediaconvert.MediaConvertClient client =
				software.amazon.awssdk.services.mediaconvert.MediaConvertClient.builder()
						.region(Region.of(s3StorageProperties.getRegion()))
						.endpointOverride(URI.create(endpoint))
						.build()) {

			CreateJobRequest.Builder requestBuilder = CreateJobRequest.builder()
					.role(roleArn)
					.jobTemplate(jobTemplateName)
					.settings(JobSettings.builder()
							.inputs(Input.builder().fileInput(inputUri).build())
							.build());

			if (properties.getQueueArn() != null && !properties.getQueueArn().isBlank()) {
				requestBuilder.queue(properties.getQueueArn());
			}

			client.createJob(requestBuilder.build());
			log.info("[MediaConvert] {} -> {} 트랜스코딩 job 요청 완료", sourceObjectKey, outputPrefix);
		}
	}

	private String requireConfigured(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(
					"media.mediaconvert.enabled=true인데 %s 설정이 비어 있습니다.".formatted(propertyName));
		}
		return value;
	}
}
