package com.artbid.infra.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3Client / S3Presigner 빈 구성.
 * media.storage.endpoint가 채워져 있으면 MinIO 같은 S3 호환 엔드포인트로 붙고,
 * 비어 있으면 SDK가 리전 기본 AWS S3 엔드포인트를 사용한다.
 */
@Configuration
@RequiredArgsConstructor
public class S3ClientConfig {

	private final S3StorageProperties properties;

	@Bean
	public S3Client s3Client() {
		var builder = S3Client.builder()
				.region(Region.of(properties.getRegion()))
				.credentialsProvider(credentialsProvider())
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(properties.isPathStyleAccess())
						.build());
		if (hasEndpoint()) {
			builder.endpointOverride(URI.create(properties.getEndpoint()));
		}
		return builder.build();
	}

	@Bean
	public S3Presigner s3Presigner() {
		var builder = S3Presigner.builder()
				.region(Region.of(properties.getRegion()))
				.credentialsProvider(credentialsProvider())
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(properties.isPathStyleAccess())
						.build());
		if (hasEndpoint()) {
			builder.endpointOverride(URI.create(properties.getEndpoint()));
		}
		return builder.build();
	}

	private boolean hasEndpoint() {
		return properties.getEndpoint() != null && !properties.getEndpoint().isBlank();
	}

	private AwsCredentialsProvider credentialsProvider() {
		String accessKey = properties.getAccessKey();
		if (accessKey != null && !accessKey.isBlank()) {
			return StaticCredentialsProvider.create(
					AwsBasicCredentials.create(accessKey, properties.getSecretKey()));
		}
		// AWS 실환경: access-key를 안 채우면 SDK 기본 자격 증명 체인(IAM 롤 등)을 사용
		return DefaultCredentialsProvider.create();
	}
}
