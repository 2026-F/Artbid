package com.artbid.media.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * MediaConvert job 완료를 주기적으로 확인한다 (media.mediaconvert.enabled=true일 때만 동작).
 * 운영에서 알림이 더 빨라야 하면 EventBridge(MediaConvert Job State Change) → SQS/웹훅 방식으로 바꿀 수 있다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "media.mediaconvert", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TranscodeStatusPoller {

	private final TranscodeService transcodeService;

	@Scheduled(fixedDelayString = "${media.mediaconvert.poll-interval-ms:60000}",
			initialDelayString = "${media.mediaconvert.poll-interval-ms:60000}")
	public void poll() {
		transcodeService.refreshProcessingJobs();
	}
}
