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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantToken;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantTokenCapability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StreamingTokenServiceTest {

	private static final Long AUCTION_ID = 1L;
	private static final Long ARTWORK_ID = 10L;
	private static final Long CONSIGNOR_ID = 100L;
	private static final String STAGE_ARN = "arn:aws:ivs:ap-northeast-2:123456789012:stage/AbCdEf123456";
	private static final String CLIENT_IP = "203.0.113.5";

	private LivestreamRepository livestreamRepository;
	private AuctionRepository auctionRepository;
	private ArtworkRepository artworkRepository;
	private IvsStageClient ivsStageClient;
	private StringRedisTemplate redisTemplate;
	private ValueOperations<String, String> valueOperations;
	private StageTokenProperties properties;
	private StreamingTokenService streamingTokenService;

	@BeforeEach
	void setup() {
		livestreamRepository = mock(LivestreamRepository.class);
		auctionRepository = mock(AuctionRepository.class);
		artworkRepository = mock(ArtworkRepository.class);
		ivsStageClient = mock(IvsStageClient.class);
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		properties = new StageTokenProperties();
		streamingTokenService = new StreamingTokenService(
				livestreamRepository, auctionRepository, artworkRepository, ivsStageClient, redisTemplate, properties);

		Livestream live = Livestream.builder().auctionId(AUCTION_ID).build();
		live.activate(STAGE_ARN, LocalDateTime.now());
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.of(live));

		Auction auction = mock(Auction.class);
		when(auction.getArtworkId()).thenReturn(ARTWORK_ID);
		when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));

		Artwork artwork = mock(Artwork.class);
		when(artwork.getConsignorId()).thenReturn(CONSIGNOR_ID);
		when(artworkRepository.findById(ARTWORK_ID)).thenReturn(Optional.of(artwork));

		when(ivsStageClient.createParticipantToken(anyString(), anyString(), any(ParticipantTokenCapability.class), any(Duration.class)))
				.thenReturn(ParticipantToken.builder()
						.token("token-value")
						.expirationTime(Instant.parse("2026-01-01T00:00:00Z"))
						.build());
	}

	@Test
	void 위탁자_본인이면_PUBLISH_토큰을_받는다() {
		StageTokenResponse response = streamingTokenService.issueToken(AUCTION_ID, CONSIGNOR_ID, CLIENT_IP);

		assertThat(response.role()).isEqualTo("PUBLISH");
		verify(ivsStageClient).createParticipantToken(
				STAGE_ARN, "member-" + CONSIGNOR_ID, ParticipantTokenCapability.PUBLISH, properties.getPublishDuration());
		verifyNoInteractions(valueOperations); // PUBLISH는 레이트리밋 대상이 아니다
	}

	@Test
	void 로그인했지만_위탁자가_아니면_SUBSCRIBE_토큰을_받는다() {
		Long bidderId = 999L;
		when(valueOperations.increment(anyString())).thenReturn(1L);

		StageTokenResponse response = streamingTokenService.issueToken(AUCTION_ID, bidderId, CLIENT_IP);

		assertThat(response.role()).isEqualTo("SUBSCRIBE");
		verify(ivsStageClient).createParticipantToken(
				STAGE_ARN, "member-" + bidderId, ParticipantTokenCapability.SUBSCRIBE, properties.getSubscribeDuration());
	}

	@Test
	void 비로그인이면_게스트_ID로_SUBSCRIBE_토큰을_받는다() {
		when(valueOperations.increment(anyString())).thenReturn(1L);

		StageTokenResponse response = streamingTokenService.issueToken(AUCTION_ID, null, CLIENT_IP);

		assertThat(response.role()).isEqualTo("SUBSCRIBE");
		verify(ivsStageClient).createParticipantToken(
				eq(STAGE_ARN), startsWith("guest-"), eq(ParticipantTokenCapability.SUBSCRIBE), eq(properties.getSubscribeDuration()));
	}

	@Test
	void 첫_시청_토큰_발급이면_레이트리밋_TTL을_건다() {
		when(valueOperations.increment(anyString())).thenReturn(1L);

		streamingTokenService.issueToken(AUCTION_ID, null, CLIENT_IP);

		verify(redisTemplate).expire("stream:token:rate:" + CLIENT_IP, properties.getSubscribeRateLimitWindow());
	}

	@Test
	void 레이트리밋을_초과하면_429용_예외를_던진다() {
		when(valueOperations.increment(anyString())).thenReturn((long) properties.getSubscribeRateLimit() + 1);

		assertThatThrownBy(() -> streamingTokenService.issueToken(AUCTION_ID, null, CLIENT_IP))
				.isInstanceOf(StageTokenRateLimitExceededException.class);
		verifyNoInteractions(ivsStageClient);
	}

	@Test
	void 방송중이_아니면_NotFound_예외를_던진다() {
		when(livestreamRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> streamingTokenService.issueToken(AUCTION_ID, null, CLIENT_IP))
				.isInstanceOf(LivestreamNotFoundException.class);
	}
}
