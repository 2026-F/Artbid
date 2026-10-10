package com.artbid.streaming.controller;

import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.exception.LivestreamAlreadyLiveException;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import com.artbid.streaming.service.StreamingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StreamingControllerTest {

	private StreamingService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(StreamingService.class);
		mvc = MockMvcBuilders.standaloneSetup(new StreamingController(service))
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
}
