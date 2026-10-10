package com.artbid.streaming.controller;

import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.dto.StageTokenResponse;
import com.artbid.streaming.exception.LivestreamAlreadyLiveException;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import com.artbid.streaming.exception.StageTokenRateLimitExceededException;
import com.artbid.streaming.service.StreamingService;
import com.artbid.streaming.service.StreamingTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.core.exception.SdkException;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StreamingControllerTest {

	private StreamingService service;
	private StreamingTokenService tokenService;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(StreamingService.class);
		tokenService = mock(StreamingTokenService.class);
		mvc = MockMvcBuilders.standaloneSetup(new StreamingController(service, tokenService))
				.setControllerAdvice(new GlobalExceptionHandler(), new StreamingExceptionHandler()).build();
	}

	private Livestream live(Long auctionId) {
		Livestream livestream = Livestream.builder().auctionId(auctionId).build();
		livestream.activate("arn:aws:ivs:ap-northeast-2:123456789012:stage/secret", LocalDateTime.now());
		return livestream;
	}

	@Test
	void 방송을_시작하면_201과_상태를_돌려주고_stageArn은_숨긴다() throws Exception {
		when(service.startStream(1L)).thenReturn(live(1L));

		mvc.perform(post("/api/auctions/1/stream/start"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.auctionId").value(1))
				.andExpect(jsonPath("$.status").value("LIVE"))
				.andExpect(jsonPath("$.stageArn").doesNotExist());
	}

	@Test
	void 이미_방송_중이면_409() throws Exception {
		when(service.startStream(1L)).thenThrow(new LivestreamAlreadyLiveException(1L));

		mvc.perform(post("/api/auctions/1/stream/start"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LIVESTREAM_ALREADY_LIVE"));
	}

	@Test
	void AWS_호출이_실패하면_502() throws Exception {
		when(service.startStream(1L)).thenThrow(SdkException.builder().message("no credentials").build());

		mvc.perform(post("/api/auctions/1/stream/start"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("IVS_UNAVAILABLE"));
	}

	@Test
	void 방송을_종료하면_204() throws Exception {
		mvc.perform(post("/api/auctions/1/stream/end"))
				.andExpect(status().isNoContent());
		verify(service).endStream(1L);
	}

	@Test
	void 방송_정보가_없으면_404() throws Exception {
		when(service.getStream(99L)).thenThrow(new LivestreamNotFoundException(99L));

		mvc.perform(get("/api/auctions/99/stream"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LIVESTREAM_NOT_FOUND"));
	}

	@Test
	void 로그인한_사용자가_토큰을_요청하면_Principal에서_회원ID를_꺼내_넘긴다() throws Exception {
		Principal principal = () -> "5";
		when(tokenService.issueToken(eq(1L), eq(5L), anyString()))
				.thenReturn(new StageTokenResponse("token-value", "SUBSCRIBE", Instant.parse("2026-01-01T00:00:00Z")));

		mvc.perform(post("/api/auctions/1/stream/tokens").principal(principal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("token-value"))
				.andExpect(jsonPath("$.role").value("SUBSCRIBE"));
	}

	@Test
	void 비로그인이면_null_회원ID로_토큰을_요청한다() throws Exception {
		when(tokenService.issueToken(eq(1L), isNull(), anyString()))
				.thenReturn(new StageTokenResponse("token-value", "SUBSCRIBE", Instant.parse("2026-01-01T00:00:00Z")));

		mvc.perform(post("/api/auctions/1/stream/tokens"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("SUBSCRIBE"));
	}

	@Test
	void 레이트리밋_초과면_429() throws Exception {
		when(tokenService.issueToken(eq(1L), isNull(), anyString()))
				.thenThrow(new StageTokenRateLimitExceededException("203.0.113.5"));

		mvc.perform(post("/api/auctions/1/stream/tokens"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("STAGE_TOKEN_RATE_LIMITED"));
	}
}
