package com.artbid.liveshow.controller;

import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.exception.ChatRateLimitExceededException;
import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.liveshow.domain.LiveShowStatus;
import com.artbid.liveshow.dto.LiveShowDtos.ShowStateResponse;
import com.artbid.liveshow.exception.LiveShowConflictException;
import com.artbid.liveshow.exception.LiveShowNotFoundException;
import com.artbid.liveshow.realtime.LiveShowSseRegistry;
import com.artbid.liveshow.service.LiveShowChatService;
import com.artbid.liveshow.service.LiveShowService;
import com.artbid.liveshow.service.LiveShowTokenService;
import com.artbid.streaming.dto.StageTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.core.exception.SdkException;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LiveShowControllerTest {

	private static final Principal VIEWER = () -> "200";

	private LiveShowService liveShowService;
	private LiveShowTokenService tokenService;
	private LiveShowChatService chatService;
	private LiveShowSseRegistry sseRegistry;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		liveShowService = mock(LiveShowService.class);
		tokenService = mock(LiveShowTokenService.class);
		chatService = mock(LiveShowChatService.class);
		sseRegistry = new LiveShowSseRegistry();
		mvc = MockMvcBuilders.standaloneSetup(
						new LiveShowController(liveShowService, tokenService, chatService),
						new LiveShowRealtimeController(sseRegistry, liveShowService))
				.setControllerAdvice(new GlobalExceptionHandler(), new LiveShowExceptionHandler())
				.build();
	}

	private ChatMessageResponse message() {
		return new ChatMessageResponse(9L, 1001L, 200L, "그림좋아", false, "안녕하세요",
				LocalDateTime.of(2026, 11, 28, 20, 0), 1L);
	}

	// ---- 토큰 ----

	@Test
	void 토큰은_로그인한_회원_ID와_클라이언트_IP로_발급한다() throws Exception {
		when(tokenService.issueToken(1L, 200L, "9.9.9.9"))
				.thenReturn(new StageTokenResponse("token-value", "SUBSCRIBE", Instant.parse("2026-11-28T12:00:00Z")));

		mvc.perform(post("/api/live-shows/1/stream/tokens").principal(VIEWER)
						.header("X-Forwarded-For", "9.9.9.9, 10.0.0.1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("SUBSCRIBE"))
				.andExpect(jsonPath("$.stageArn").doesNotExist());
	}

	@Test
	void 비로그인도_시청_토큰을_요청할_수_있다() throws Exception {
		when(tokenService.issueToken(eq(1L), isNull(), anyString()))
				.thenReturn(new StageTokenResponse("token-value", "SUBSCRIBE", Instant.parse("2026-11-28T12:00:00Z")));

		mvc.perform(post("/api/live-shows/1/stream/tokens"))
				.andExpect(status().isOk());
		verify(tokenService).issueToken(eq(1L), isNull(), anyString());
	}

	@Test
	void 방송_중이_아니면_토큰은_409_없는_라이브는_404_AWS_실패는_502() throws Exception {
		when(tokenService.issueToken(eq(1L), any(), anyString())).thenThrow(new LiveShowConflictException("방송 중 아님"));
		when(tokenService.issueToken(eq(99L), any(), anyString())).thenThrow(new LiveShowNotFoundException(99L));
		when(tokenService.issueToken(eq(2L), any(), anyString()))
				.thenThrow(SdkException.builder().message("no credentials").build());

		mvc.perform(post("/api/live-shows/1/stream/tokens"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LIVE_SHOW_CONFLICT"));
		mvc.perform(post("/api/live-shows/99/stream/tokens"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LIVE_SHOW_NOT_FOUND"));
		mvc.perform(post("/api/live-shows/2/stream/tokens"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("IVS_UNAVAILABLE"));
	}

	// ---- 채팅 ----

	@Test
	void 로그인한_회원은_채팅을_보낸다() throws Exception {
		when(chatService.send(1L, 200L, "안녕하세요")).thenReturn(message());

		mvc.perform(post("/api/live-shows/1/chat/messages").principal(VIEWER)
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕하세요\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.showId").value(1))
				.andExpect(jsonPath("$.auctionId").value(1001));
	}

	@Test
	void 비로그인으로_채팅을_보내면_401() throws Exception {
		mvc.perform(post("/api/live-shows/1/chat/messages")
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕하세요\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LIVE_SHOW_AUTH_REQUIRED"));
		verifyNoInteractions(chatService);
	}

	@Test
	void 빈_내용이나_200자_초과_채팅은_400() throws Exception {
		mvc.perform(post("/api/live-shows/1/chat/messages").principal(VIEWER)
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("LIVE_SHOW_INVALID_REQUEST"));
		mvc.perform(post("/api/live-shows/1/chat/messages").principal(VIEWER)
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + "a".repeat(201) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("LIVE_SHOW_INVALID_REQUEST"));
		verifyNoInteractions(chatService);
	}

	@Test
	void 너무_빨리_보내면_429() throws Exception {
		when(chatService.send(1L, 200L, "hi")).thenThrow(new ChatRateLimitExceededException());

		mvc.perform(post("/api/live-shows/1/chat/messages").principal(VIEWER)
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("CHAT_RATE_LIMITED"));
	}

	@Test
	void 대화_조회는_비로그인도_할_수_있다() throws Exception {
		when(chatService.getMessages(1L, null, null)).thenReturn(List.of(message()));

		mvc.perform(get("/api/live-shows/1/chat/messages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].content").value("안녕하세요"));
	}

	// ---- SSE ----

	@Test
	void 구독하면_현재_상태를_바로_한_번_보낸다() throws Exception {
		when(liveShowService.getState(1L)).thenReturn(new ShowStateResponse(1L, LiveShowStatus.LIVE, 11L, null));

		String body = mvc.perform(get("/ws/live-shows/1"))
				.andExpect(request().asyncStarted())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("event:show-state").contains("\"currentLotId\":11");
		assertThat(sseRegistry.subscriberCount(1L)).isEqualTo(1);
	}

	@Test
	void 없는_라이브를_구독하면_바로_닫는다() throws Exception {
		when(liveShowService.getState(99L)).thenThrow(new LiveShowNotFoundException(99L));

		mvc.perform(get("/ws/live-shows/99"));

		assertThat(sseRegistry.subscriberCount(99L)).isZero();
	}
}
