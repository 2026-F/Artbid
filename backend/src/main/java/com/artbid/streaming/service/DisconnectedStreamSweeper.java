package com.artbid.streaming.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 끊긴 채로 방치된 방송을 주기적으로 정리한다. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class DisconnectedStreamSweeper {

	private final StreamingService streamingService;

	@Scheduled(fixedDelayString = "${app.streaming.reconnect.sweep-interval-ms:30000}",
			initialDelayString = "${app.streaming.reconnect.sweep-interval-ms:30000}")
	public void sweep() {
		streamingService.endAbandonedDisconnectedStreams();
	}
}
