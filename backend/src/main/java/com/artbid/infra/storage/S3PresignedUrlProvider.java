package com.artbid.infra.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class S3PresignedUrlProvider {

	private final S3Presigner s3Presigner;
	private final S3StorageProperties properties;

	/**
	 * 사진/동영상/3D 모델(glTF/GLB) 공통으로 사용하는 업로드용 presigned URL 발급.
	 * 클라이언트는 이 URL로 파일을 직접 PUT하고(서버를 거치지 않음), 끝나면
	 * /complete API로 objectKey를 알려준다.
	 */
	public String issueUploadUrl(String objectKey, String contentType) {
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(properties.getBucket())
				.key(objectKey)
				.contentType(contentType)
				.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.signatureDuration(properties.getPresignExpiration())
				.putObjectRequest(putObjectRequest)
				.build();

		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
		return presigned.url().toString();
	}

	/**
	 * 업로드가 끝난 객체를 바로 감상/조회할 수 있는 URL.
	 * media.storage.public-base-url이 있으면 그걸 쓰고, 없으면 endpoint + bucket으로 구성한다.
	 * (버킷을 공개 읽기로 열어둔 로컬/PoC 환경 기준 — S3BucketInitializer 참고.
	 *  운영에서 비공개 버킷을 쓴다면 이 메서드 대신 presigned GET URL 발급 방식으로 바꿔야 한다.)
	 */
	public String issuePublicUrl(String objectKey) {
		String base = properties.getPublicBaseUrl();
		if (base == null || base.isBlank()) {
			base = properties.getEndpoint() + "/" + properties.getBucket();
		}
		return stripTrailingSlash(base) + "/" + objectKey;
	}

	private String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
