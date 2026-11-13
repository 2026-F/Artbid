package com.artbid.chat.controller;

import com.artbid.chat.dto.ChatMessageResponse;
import com.artbid.chat.exception.ChatNotAvailableException;
import com.artbid.chat.exception.ChatRateLimitExceededException;
import com.artbid.chat.service.LiveChatService;
import com.artbid.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveChatControllerTest {

	private static final Principal BIDDER = () -> "7";
	private static final String URL = "/api/auctions/1/chat/messages";

	private LiveChatService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(LiveChatService.class);
		mvc = MockMvcBuilders.standaloneSetup(new LiveChatController(service))
				.setControllerAdvice(new GlobalExceptionHandler(), new LiveChatExceptionHandler()).build();
	}

	private ChatMessageResponse response(String content) {
		return new ChatMessageResponse(3L, 1L, 7L, "홍길동", false, content, LocalDateTime.of(2026, 11, 13, 20, 0));
	}

	@Test
	void 로그인한_회원이_보내면_201과_메시지를_돌려준다() throws Exception {
		when(service.send(1L, 7L, "응원합니다")).thenReturn(response("응원합니다"));

		mvc.perform(post(URL).principal(BIDDER).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"응원합니다\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(3))
				.andExpect(jsonPath("$.senderName").value("홍길동"))
				.andExpect(jsonPath("$.host").value(false))
				.andExpect(jsonPath("$.content").value("응원합니다"));
	}

	@Test
	void 본문에_다른_회원_ID를_넣어도_토큰의_회원으로_보낸다() throws Exception {
		when(service.send(1L, 7L, "안녕하세요")).thenReturn(response("안녕하세요"));

		mvc.perform(post(URL).principal(BIDDER).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"안녕하세요\",\"senderId\":999,\"host\":true}"))
				.andExpect(status().isCreated());

		verify(service).send(1L, 7L, "안녕하세요");
	}

	@Test
	void 비로그인이면_401() throws Exception {
		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕하세요\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("CHAT_AUTH_REQUIRED"));

		verifyNoInteractions(service);
	}

	@Test
	void 빈_메시지는_400() throws Exception {
		mvc.perform(post(URL).principal(BIDDER).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CHAT_INVALID_MESSAGE"));

		verifyNoInteractions(service);
	}

	@Test
	void 길이_제한을_넘으면_400() throws Exception {
		String tooLong = "가".repeat(201);

		mvc.perform(post(URL).principal(BIDDER).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + tooLong + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CHAT_INVALID_MESSAGE"));
	}

	@Test
	void 본문이_없으면_400() throws Exception {
		mvc.perform(post(URL).principal(BIDDER).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CHAT_INVALID_MESSAGE"));
	}

	@Test
	void 방송이_열려_있지_않으면_409() throws Exception {
		when(service.send(any(), any(), any())).thenThrow(new ChatNotAvailableException(1L));

		mvc.perform(post(URL).principal(BIDDER).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"안녕하세요\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CHAT_NOT_AVAILABLE"));
	}

	@Test
	void 너무_빠르게_보내면_429() throws Exception {
		when(service.send(any(), any(), any())).thenThrow(new ChatRateLimitExceededException());

		mvc.perform(post(URL).principal(BIDDER).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"안녕하세요\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("CHAT_RATE_LIMITED"));
	}

	@Test
	void 대화_조회는_비로그인도_가능하다() throws Exception {
		when(service.getMessages(1L, 30L, 20)).thenReturn(List.of(response("먼저"), response("나중")));

		mvc.perform(get(URL).param("beforeId", "30").param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].content").value("먼저"));
	}
}
