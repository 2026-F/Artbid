package com.artbid.infra.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AWS MediaConvert 연동 설정. AWS 계정/역할/큐/Job Template이 준비되기 전까지는
 * enabled=false(기본값)로 두면 실제 API 호출 없이 로그만 남긴다.
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
}
