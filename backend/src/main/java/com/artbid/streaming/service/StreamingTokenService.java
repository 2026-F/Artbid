package com.artbid.streaming.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.streaming.config.StageTokenProperties;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.dto.StageTokenResponse;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import com.artbid.streaming.exception.StageTokenRateLimitExceededException;
import com.artbid.streaming.repository.LivestreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantToken;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantTokenCapability;

import java.util.UUID;

/**
 * 스테이지 참여 토큰 발급. 위탁자 본인에게는 PUBLISH, 그 외에는(비로그인 포함) SUBSCRIBE를 내준다.
 * 요청에 "어떤 역할을 받고 싶다"는 입력은 없다 — 위탁자인지 아닌지로 서버가 정한다.
 */
@Service
@RequiredArgsConstructor
public class StreamingTokenService {

	private static final String RATE_LIMIT_KEY_PREFIX = "stream:token:rate:";

	private final LivestreamRepository livestreamRepository;
	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;
	private final IvsStageClient ivsStageClient;
	private final StringRedisTemplate redisTemplate;
	private final StageTokenProperties properties;

	@Transactional(readOnly = true)
	public StageTokenResponse issueToken(Long auctionId, Long requesterId, String clientIp) {
		Livestream livestream = livestreamRepository.findByAuctionId(auctionId)
				.filter(Livestream::isLive)
				.orElseThrow(() -> new LivestreamNotFoundException(auctionId));

		String userId = participantUserId(requesterId);

		if (isConsignor(auctionId, requesterId)) {
			ParticipantToken token = ivsStageClient.createParticipantToken(
					livestream.getStageArn(), userId, ParticipantTokenCapability.PUBLISH, properties.getPublishDuration());
			return StageTokenResponse.from(token, ParticipantTokenCapability.PUBLISH);
		}

		enforceSubscribeRateLimit(clientIp);
		ParticipantToken token = ivsStageClient.createParticipantToken(
				livestream.getStageArn(), userId, ParticipantTokenCapability.SUBSCRIBE, properties.getSubscribeDuration());
		return StageTokenResponse.from(token, ParticipantTokenCapability.SUBSCRIBE);
	}

	/** 이 경매의 위탁 작품을 올린 본인인지 확인한다. 비로그인이면 당연히 아니다. */
	private boolean isConsignor(Long auctionId, Long requesterId) {
		if (requesterId == null) {
			return false;
		}
		return auctionRepository.findById(auctionId)
				.map(Auction::getArtworkId)
				.flatMap(artworkRepository::findById)
				.map(Artwork::getConsignorId)
				.map(requesterId::equals)
				.orElse(false);
	}

	/**
	 * IP별로 설정된 기간 동안 발급 가능한 횟수를 센다. 첫 요청에만 TTL을 걸어서,
	 * 키가 사라지지 않는 한 기간이 늘어지지 않게 한다(고정 윈도우 방식).
	 */
	private void enforceSubscribeRateLimit(String clientIp) {
		String key = RATE_LIMIT_KEY_PREFIX + clientIp;
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redisTemplate.expire(key, properties.getSubscribeRateLimitWindow());
		}
		if (count != null && count > properties.getSubscribeRateLimit()) {
			throw new StageTokenRateLimitExceededException(clientIp);
		}
	}

	/** 로그인했으면 회원 ID를, 아니면 매 요청마다 새로 만드는 게스트 ID를 쓴다. */
	private String participantUserId(Long requesterId) {
		return requesterId != null ? "member-" + requesterId : "guest-" + UUID.randomUUID();
	}
}
