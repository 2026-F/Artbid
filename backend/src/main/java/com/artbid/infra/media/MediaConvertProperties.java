package com.artbid.infra.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AWS MediaConvert 연동 설정. AWS 계정/역할/큐/Job Template이 준비되기 전까지는
 * enabled=false(기본값)로 두면 실제 API 호출 없이 원본 영상을 그대로 감상용으로 쓴다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "media.mediaconvert")
public class MediaConvertProperties {

	private boolean enabled = false;

	/** 계정별 MediaConvert 전용 엔드포인트 (AWS 콘솔 MediaConvert > Account 에서 확인) */
	private String endpoint;

	private String roleArn;

	/** 비워두면 계정 기본(default) 큐를 사용한다. */
	private String queueArn;

	/** AWS 콘솔에서 미리 만들어둔 Job Template 이름 (출력 코덱/해상도/HLS 설정은 여기서 관리) */
	private String jobTemplateName;

	/**
	 * Job Template의 출력 Destination 경로 (media.storage.bucket 기준 object key prefix).
	 * 템플릿 Destination을 s3://{bucket}/{outputKeyPrefix} 로 맞춰야 한다.
	 * MediaConvert는 출력 파일명을 "입력 파일명(확장자 제외) + outputSuffix"로 만든다.
	 */
	private String outputKeyPrefix = "transcoded/";

	/** 감상용으로 쓸 출력 파일의 접미사 (HLS 마스터 플레이리스트 기준 .m3u8) */
	private String outputSuffix = ".m3u8";

	/** 진행 중인 job 상태를 확인하는 주기 (밀리초) */
	private long pollIntervalMs = 60_000;
}
