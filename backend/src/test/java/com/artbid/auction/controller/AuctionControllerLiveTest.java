package com.artbid.auction.controller;

import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveService;
import com.artbid.auction.live.AuctionLiveView;
import com.artbid.auction.service.AuctionReopenService;
import com.artbid.auction.service.AuctionService;
import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.common.exception.InvalidAuctionRequestException;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuctionControllerLiveTest {

	@Mock
	private AuctionService auctionService;
	@Mock
	private AuctionLiveService auctionLiveService;
	@Mock
	private AuctionReopenService auctionReopenService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		mockMvc = MockMvcBuilders.standaloneSetup(new AuctionController(auctionService, auctionLiveService, auctionReopenService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setMessageConverters(
						new StringHttpMessageConverter(StandardCharsets.UTF_8),
						new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	@Test
	void 실시간_상태를_조회한다() throws Exception {
		LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
		when(auctionLiveService.getView(1L, AuctionLiveReason.SNAPSHOT)).thenReturn(new AuctionLiveView(
				1L, 15_000L, 1_000L, 16_000L, now.plusMinutes(1), AuctionStatus.ONGOING,
				60L, now, 0L, AuctionLiveReason.SNAPSHOT, false));

		mockMvc.perform(get("/api/auctions/1/live"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPrice").value(15_000))
				.andExpect(jsonPath("$.nextMinPrice").value(16_000))
				.andExpect(jsonPath("$.remainingSeconds").value(60))
				.andExpect(jsonPath("$.status").value("ONGOING"))
				.andExpect(jsonPath("$.reason").value("SNAPSHOT"));
	}

	@Test
	void 없는_경매면_404() throws Exception {
		when(auctionLiveService.getView(99L, AuctionLiveReason.SNAPSHOT))
				.thenThrow(new IllegalArgumentException("경매를 찾을 수 없습니다: 99"));

		mockMvc.perform(get("/api/auctions/99/live"))
				.andExpect(status().isNotFound());
	}

	private static final String CREATE_BODY = """
			{"artworkId":10,"startPrice":100000,"minBidUnit":10000,
			 "previewStart":"2026-11-10T10:00:00","previewEnd":"2026-11-11T10:00:00","auctionEndAt":"2026-11-12T10:00:00"}
			""";

	@Test
	void 비관리자는_경매를_생성할_수_없다() throws Exception {
		mockMvc.perform(post("/api/auctions").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/auctions").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
						.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR))
				.andExpect(status().isForbidden());
		verifyNoInteractions(auctionService);
	}

	@Test
	void 비관리자는_경매를_다시_열_수_없다() throws Exception {
		mockMvc.perform(post("/api/auctions/1/reopen").contentType(MediaType.APPLICATION_JSON)
						.content("{\"durationMinutes\":60}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/auctions/1/reopen").contentType(MediaType.APPLICATION_JSON)
						.content("{\"durationMinutes\":60}")
						.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR))
				.andExpect(status().isForbidden());
		verifyNoInteractions(auctionReopenService);
	}

	@Test
	void 진행_시간이_잘못되면_400을_반환한다() throws Exception {
		when(auctionReopenService.reopen(1L, 0)).thenThrow(new InvalidAuctionRequestException("진행 시간을 확인해주세요."));

		mockMvc.perform(post("/api/auctions/1/reopen").contentType(MediaType.APPLICATION_JSON)
						.content("{\"durationMinutes\":0}")
						.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.ADMIN))
				.andExpect(status().isBadRequest());
	}
}
