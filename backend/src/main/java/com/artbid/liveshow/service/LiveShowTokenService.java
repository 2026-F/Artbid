package com.artbid.liveshow.service;

import com.artbid.infra.redis.FixedWindowRateLimiter;
import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.liveshow.domain.LiveShow;
import com.artbid.liveshow.exception.LiveShowConflictException;
import com.artbid.liveshow.exception.LiveShowNotFoundException;
import com.artbid.liveshow.repository.LiveShowRepository;
import com.artbid.streaming.config.StageTokenProperties;
import com.artbid.streaming.dto.StageTokenResponse;
import com.artbid.streaming.exception.StageTokenRateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantToken;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantTokenCapability;

import java.util.UUID;

/**
 * 라이브 편성 스테이지 참여 토큰. 경매 단위 토큰(StreamingTokenService)과 같은 규칙을 쓴다.
 * - 진행자(LiveShow.hostId)는 PUBLISH, 그 외는 비로그인 포함 SUBSCRIBE. 요청자가 역할을 고르지 않는다.
 * - 시청 토큰은 IP별 발급 횟수를 제한한다. 유효시간과 제한값은 app.streaming.token 설정을 같이 쓴다.
 * 토큰 값은 기록에 남기지 않는다. AWS 호출은 트랜잭션 밖에서 한다.
 */
@Service
@RequiredArgsConstructor
public class LiveShowTokenService {

	private static final String RATE_LIMIT_KEY_PREFIX = "live-show:token:rate:";

	private final LiveShowRepository showRepository;
	private final IvsStageClient ivsStageClient;
	private final FixedWindowRateLimiter rateLimiter;
	private final StageTokenProperties properties;

	public StageTokenResponse issueToken(Long showId, Long requesterId, String clientIp) {
		LiveShow show = showRepository.findById(showId).orElseThrow(() -> new LiveShowNotFoundException(showId));
		if (!show.isLive() || show.getStageArn() == null) {
			throw new LiveShowConflictException("방송 중인 라이브만 참여할 수 있습니다. 현재 상태: " + show.getStatus());
		}

		String userId = requesterId != null ? "member-" + requesterId : "guest-" + UUID.randomUUID();

		if (show.isHost(requesterId)) {
			ParticipantToken token = ivsStageClient.createParticipantToken(
					show.getStageArn(), userId, ParticipantTokenCapability.PUBLISH, properties.getPublishDuration());
			return StageTokenResponse.from(token, ParticipantTokenCapability.PUBLISH);
		}

		enforceSubscribeRateLimit(clientIp);
		ParticipantToken token = ivsStageClient.createParticipantToken(
				show.getStageArn(), userId, ParticipantTokenCapability.SUBSCRIBE, properties.getSubscribeDuration());
		return StageTokenResponse.from(token, ParticipantTokenCapability.SUBSCRIBE);
	}

	private void enforceSubscribeRateLimit(String clientIp) {
		String key = RATE_LIMIT_KEY_PREFIX + clientIp;
		if (rateLimiter.increment(key, properties.getSubscribeRateLimitWindow()) > properties.getSubscribeRateLimit()) {
			throw new StageTokenRateLimitExceededException(clientIp);
		}
	}
}
