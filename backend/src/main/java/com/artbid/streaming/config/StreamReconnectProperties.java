package com.artbid.streaming.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 위탁자 연결 끊김(DISCONNECTED) 상태를 얼마나 봐줄지에 대한 설정. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.streaming.reconnect")
public class StreamReconnectProperties {

	/** 이 시간 동안 재연결하지 못하면 자동으로 방송을 종료(ENDED)한다. */
	private Duration timeout = Duration.ofMinutes(2);

	/** 끊긴 방송을 얼마나 자주 훑어서 timeout을 넘겼는지 확인할지. */
	private long sweepIntervalMs = 30_000;
}
