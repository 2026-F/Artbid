package com.artbid.streaming.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 스테이지 참여 토큰 발급 설정.
 * 시청(SUBSCRIBE) 토큰은 비로그인으로도 발급되므로 유효시간을 짧게 두고
 * IP별 발급 횟수를 제한한다. 송출(PUBLISH) 토큰은 위탁자 본인만 받으므로
 * 더 길게 둬도 된다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.streaming.token")
public class StageTokenProperties {

	private Duration publishDuration = Duration.ofMinutes(180);

	private Duration subscribeDuration = Duration.ofMinutes(10);

	/** 시청 토큰 발급 IP 레이트리밋: 이 기간 동안 허용하는 최대 발급 횟수 */
	private int subscribeRateLimit = 20;

	private Duration subscribeRateLimitWindow = Duration.ofMinutes(10);
}
