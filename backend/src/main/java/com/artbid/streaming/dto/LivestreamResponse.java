package com.artbid.streaming.dto;

import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.domain.LivestreamStatus;

import java.time.LocalDateTime;

/**
 * 방송 상태 응답. stageArn은 AWS 계정 정보가 들어 있는 내부 식별자라 내려주지 않는다.
 * 프론트는 스테이지에 직접 접근하지 않고, 참여 토큰 발급 API로 받은 토큰만 쓴다.
 */
public record LivestreamResponse(
		Long id,
		Long auctionId,
		LivestreamStatus status,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		LocalDateTime disconnectedAt
) {

	public static LivestreamResponse from(Livestream livestream) {
		return new LivestreamResponse(
				livestream.getId(),
				livestream.getAuctionId(),
				livestream.getStatus(),
				livestream.getStartedAt(),
				livestream.getEndedAt(),
				livestream.getDisconnectedAt());
	}
}
