package com.artbid.media.service;

import com.artbid.infra.media.MediaConvertClient;
import com.artbid.infra.media.MediaConvertProperties;
import com.artbid.infra.media.TranscodeJobResult;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.infra.storage.S3StorageProperties;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.TranscodeStatus;
import com.artbid.media.repository.ArtworkMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.Optional;

/**
 * 동영상 트랜스코딩 요청과 완료 처리.
 * 1) 업로드 완료 시 start(): MediaConvert job 요청 → PROCESSING (비활성화면 SKIPPED, 요청 실패면 FAILED)
 * 2) TranscodeStatusPoller가 주기적으로 refreshProcessingJobs(): job이 끝났으면 COMPLETED/FAILED로 전환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeService {

	private final MediaConvertClient mediaConvertClient;
	private final MediaConvertProperties mediaConvertProperties;
	private final ArtworkMediaRepository artworkMediaRepository;
	private final S3PresignedUrlProvider s3PresignedUrlProvider;
	private final S3StorageProperties s3StorageProperties;
	private final S3Client s3Client;

	/**
	 * 트랜스코딩을 요청하고 결과에 맞게 미디어 상태를 바꾼다. (저장은 호출하는 쪽에서)
	 * AWS 호출이 실패해도 업로드 완료 처리 자체는 실패시키지 않고 FAILED로 남긴다.
	 */
	public void start(ArtworkMedia media, String sourceObjectKey) {
		try {
			Optional<String> jobId = mediaConvertClient.requestTranscode(sourceObjectKey);
			if (jobId.isPresent()) {
				media.startTranscode(jobId.get());
			} else {
				media.skipTranscode();
			}
		} catch (RuntimeException e) {
			log.warn("[Transcode] {} 트랜스코딩 요청 실패 — 원본으로 대체합니다", sourceObjectKey, e);
			media.failTranscode("트랜스코딩 요청 실패: " + e.getMessage());
		}
	}

	/**
	 * 진행 중인 job들의 상태를 확인해 끝난 것은 완료/실패로 반영한다.
	 * 한 건이 실패해도 나머지는 계속 처리한다.
	 */
	public void refreshProcessingJobs() {
		List<ArtworkMedia> processing =
				artworkMediaRepository.findTop50ByTranscodeStatusOrderByIdAsc(TranscodeStatus.PROCESSING);

		for (ArtworkMedia media : processing) {
			try {
				refresh(media);
			} catch (RuntimeException e) {
				// 일시적인 AWS 오류일 수 있으니 상태는 그대로 두고 다음 주기에 다시 확인
				log.warn("[Transcode] media {} (jobId={}) 상태 확인 실패", media.getId(), media.getTranscodeJobId(), e);
			}
		}
	}

	private void refresh(ArtworkMedia media) {
		TranscodeJobResult result = mediaConvertClient.getJobResult(media.getTranscodeJobId());

		switch (result.status()) {
			case IN_PROGRESS -> {
				return;
			}
			case FAILED -> media.failTranscode(result.errorMessage());
			case COMPLETE -> {
				String outputKey = outputObjectKey(media.getSourceUrl());
				if (existsInStorage(outputKey)) {
					media.completeTranscode(s3PresignedUrlProvider.issuePublicUrl(outputKey));
				} else {
					media.failTranscode("job은 완료됐지만 출력 파일을 찾을 수 없습니다: " + outputKey
							+ " (Job Template Destination과 media.mediaconvert.output-key-prefix가 같은지 확인)");
				}
			}
		}

		artworkMediaRepository.save(media);
		log.info("[Transcode] media {} -> {}", media.getId(), media.getTranscodeStatus());
	}

	/**
	 * MediaConvert는 출력 파일명을 "입력 파일명(확장자 제외) + 접미사"로 만든다.
	 * 예) 원본 .../artworks/1/media/3f2a.mp4 → transcoded/3f2a.m3u8
	 */
	String outputObjectKey(String sourceUrl) {
		String fileName = sourceUrl.substring(sourceUrl.lastIndexOf('/') + 1);
		int dot = fileName.lastIndexOf('.');
		String baseName = dot == -1 ? fileName : fileName.substring(0, dot);

		String prefix = mediaConvertProperties.getOutputKeyPrefix();
		if (prefix == null) {
			prefix = "";
		} else if (!prefix.isEmpty() && !prefix.endsWith("/")) {
			prefix = prefix + "/";
		}
		return prefix + baseName + mediaConvertProperties.getOutputSuffix();
	}

	private boolean existsInStorage(String objectKey) {
		try {
			s3Client.headObject(HeadObjectRequest.builder()
					.bucket(s3StorageProperties.getBucket())
					.key(objectKey)
					.build());
			return true;
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				return false;
			}
			throw e;
		}
	}
}
