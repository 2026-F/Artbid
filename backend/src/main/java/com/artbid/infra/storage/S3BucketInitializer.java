package com.artbid.infra.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

/**
 * 로컬 개발(MinIO) 편의용 초기화기. media.storage.create-bucket-if-missing=true일 때만 동작하며,
 * 버킷이 없으면 만들고 공개 읽기 정책을 걸어서 업로드된 사진/동영상/3D 모델을 별도 인증 없이
 * URL로 바로 열람할 수 있게 한다 (AR/3D 뷰어가 presigned URL 없이 바로 로드하기 위함).
 *
 * 운영(AWS) 환경에서는 이 값을 false로 두고 버킷/정책을 인프라(Terraform 등)로 관리하는 걸 권장.
 * MinIO가 아직 안 떠 있어도(docker compose --profile media up 안 한 경우) 앱 기동 자체가
 * 막히지 않도록 예외는 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3BucketInitializer implements ApplicationRunner {

	private final S3Client s3Client;
	private final S3StorageProperties properties;

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isCreateBucketIfMissing()) {
			return;
		}

		String bucket = properties.getBucket();
		try {
			boolean exists = s3Client.listBuckets().buckets().stream()
					.anyMatch(b -> b.name().equals(bucket));
			if (!exists) {
				s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
				log.info("[S3] 버킷 '{}' 생성 완료", bucket);
			}
			s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
					.bucket(bucket)
					.policy(publicReadPolicy(bucket))
					.build());
			log.info("[S3] 버킷 '{}' 공개 읽기 정책 적용 완료", bucket);
		} catch (RuntimeException e) {
			log.warn("[S3] 버킷 초기화 실패 — MinIO가 떠 있는지 확인하세요 "
					+ "(docker compose --profile media up -d minio): {}", e.getMessage());
		}
	}

	private String publicReadPolicy(String bucket) {
		return """
				{
				  "Version": "2012-10-17",
				  "Statement": [
				    {
				      "Effect": "Allow",
				      "Principal": "*",
				      "Action": "s3:GetObject",
				      "Resource": "arn:aws:s3:::%s/*"
				    }
				  ]
				}
				""".formatted(bucket);
	}
}
