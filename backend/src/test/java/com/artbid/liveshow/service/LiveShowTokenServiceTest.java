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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantToken;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantTokenCapability;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class LiveShowTokenServiceTest {

	private static final Long SHOW_ID = 1L;
	private static final Long HOST_ID = 100L;

	private LiveShowRepository showRepository;
	private IvsStageClient ivsStageClient;
	private FixedWindowRateLimiter rateLimiter;
	private StageTokenProperties properties;
	private LiveShowTokenService service;

	@BeforeEach
	void setUp() {
		showRepository = mock(LiveShowRepository.class);
		ivsStageClient = mock(IvsStageClient.class);
		rateLimiter = mock(FixedWindowRateLimiter.class);
		properties = new StageTokenProperties();
		service = new LiveShowTokenService(showRepository, ivsStageClient, rateLimiter, properties);
		when(rateLimiter.increment(anyString(), any())).thenReturn(1L);
		when(ivsStageClient.createParticipantToken(anyString(), anyString(), any(), any()))
				.thenReturn(ParticipantToken.builder().token("token-value").expirationTime(Instant.now()).build());
	}

	private LiveShow show(boolean live) {
		LiveShow show = LiveShow.create(HOST_ID, "특별전", null, null, LocalDateTime.now());
		ReflectionTestUtils.setField(show, "id", SHOW_ID);
		if (live) {
			show.start("arn:stage", LocalDateTime.now());
		}
		when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
		return show;
	}

	@Test
	void 진행자는_송출_토큰을_받는다() {
		show(true);

		StageTokenResponse response = service.issueToken(SHOW_ID, HOST_ID, "1.1.1.1");

		assertThat(response.role()).isEqualTo("PUBLISH");
		verify(ivsStageClient).createParticipantToken(eq("arn:stage"), eq("member-100"),
				eq(ParticipantTokenCapability.PUBLISH), eq(properties.getPublishDuration()));
		verifyNoInteractions(rateLimiter);
	}

	@Test
	void 다른_회원과_비로그인은_시청_토큰을_받는다() {
		show(true);

		assertThat(service.issueToken(SHOW_ID, 200L, "1.1.1.1").role()).isEqualTo("SUBSCRIBE");
		assertThat(service.issueToken(SHOW_ID, null, "1.1.1.1").role()).isEqualTo("SUBSCRIBE");

		verify(ivsStageClient).createParticipantToken(eq("arn:stage"), eq("member-200"),
				eq(ParticipantTokenCapability.SUBSCRIBE), eq(properties.getSubscribeDuration()));
		verify(ivsStageClient).createParticipantToken(eq("arn:stage"), startsWith("guest-"),
				eq(ParticipantTokenCapability.SUBSCRIBE), any());
	}

	@Test
	void 시작_전이거나_끝난_라이브는_409() {
		show(false);
		assertThatThrownBy(() -> service.issueToken(SHOW_ID, HOST_ID, "1.1.1.1"))
				.isInstanceOf(LiveShowConflictException.class);

		LiveShow ended = show(true);
		ended.end(LocalDateTime.now());
		assertThatThrownBy(() -> service.issueToken(SHOW_ID, 200L, "1.1.1.1"))
				.isInstanceOf(LiveShowConflictException.class);

		verifyNoInteractions(ivsStageClient);
	}

	@Test
	void 없는_라이브는_404() {
		when(showRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.issueToken(99L, null, "1.1.1.1"))
				.isInstanceOf(LiveShowNotFoundException.class);
	}

	@Test
	void 시청_토큰은_IP별_발급_횟수를_넘으면_막는다() {
		show(true);
		when(rateLimiter.increment(eq("live-show:token:rate:1.1.1.1"), any()))
				.thenReturn((long) properties.getSubscribeRateLimit() + 1);

		assertThatThrownBy(() -> service.issueToken(SHOW_ID, null, "1.1.1.1"))
				.isInstanceOf(StageTokenRateLimitExceededException.class);
		verify(ivsStageClient, never()).createParticipantToken(any(), any(), any(), any());
	}
}
