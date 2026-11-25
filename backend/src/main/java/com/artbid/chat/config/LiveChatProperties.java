package com.artbid.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 라이브 채팅 설정. 한 회원이 짧은 시간에 화면을 도배하지 못하게 전송 횟수를 제한한다. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.chat")
public class LiveChatProperties {

	/** 회원별 전송 제한: 이 기간 동안 허용하는 최대 메시지 수 */
	private int rateLimit = 5;

	private Duration rateLimitWindow = Duration.ofSeconds(10);
}
