package com.artbid.infra.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * S3(및 S3 호환 스토리지) 연동 설정.
 * 로컬 개발 기본값은 docker-compose의 MinIO(--profile media)를 가리킨다.
 * 실제 AWS로 전환할 때는 endpoint/access-key/secret-key를 비우고
 * region/bucket만 채우면 SDK 기본 자격 증명 체인(IAM 롤 등)을 사용한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "media.storage")
public class S3StorageProperties {

	/** MinIO 등 S3 호환 엔드포인트. 실제 AWS S3를 쓸 때는 비워둔다. */
	private String endpoint;

	private String region = "us-east-1";

	private String bucket = "artbid-media";

	private String accessKey;

	private String secretKey;

	/** MinIO 등 S3 호환 스토리지는 보통 path-style 접근이 필요하다. */
	private boolean pathStyleAccess = false;

	/**
	 * 업로드된 객체를 바로 조회할 때 쓸 공개 base URL.
	 * 비워두면 endpoint + bucket 조합으로 구성한다 (버킷이 공개 읽기 정책일 때만 유효).
	 */
	private String publicBaseUrl;

	private Duration presignExpiration = Duration.ofMinutes(10);

	/** 로컬 개발 편의용. true면 기동 시 버킷이 없으면 만들고 공개 읽기 정책을 건다. */
	private boolean createBucketIfMissing = false;
}
