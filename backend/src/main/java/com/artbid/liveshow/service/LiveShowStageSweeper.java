package com.artbid.liveshow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종료할 때 IVS 스테이지 삭제에 실패해 남은 스테이지를 주기적으로 지운다.
 * 스케줄링은 DisconnectedStreamSweeper의 @EnableScheduling으로 켜져 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveShowStageSweeper {

	private final LiveShowService liveShowService;

	@Scheduled(fixedDelayString = "${app.live-show.stage-sweep-interval-ms:300000}",
			initialDelayString = "${app.live-show.stage-sweep-interval-ms:300000}")
	public void sweep() {
		int cleaned = liveShowService.cleanUpEndedStages();
		if (cleaned > 0) {
			log.info("[LiveShow] 남은 IVS 스테이지 {}개 정리", cleaned);
		}
	}
}
