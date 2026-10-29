package com.artbid.settlement.controller;

import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.settlement.domain.PaymentStatus;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.exception.SettlementAccessDeniedException;
import com.artbid.settlement.exception.SettlementNotFoundException;
import com.artbid.settlement.service.SettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementControllerTest {

	@Mock
	private SettlementService settlementService;

	private MockMvc mockMvc;
	private final Principal winner = () -> "2";

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		mockMvc = MockMvcBuilders.standaloneSetup(new SettlementController(settlementService))
				.setControllerAdvice(new SettlementExceptionHandler())
				.setMessageConverters(
						new StringHttpMessageConverter(StandardCharsets.UTF_8),
						new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	private Settlement settlement() {
		Settlement settlement = Settlement.create(5L, 2L, 100_000, 10_000, 30_000, LocalDateTime.of(2026, 11, 1, 12, 0));
		ReflectionTestUtils.setField(settlement, "id", 1L);
		return settlement;
	}

	@Test
	void 로그인한_당사자는_정산을_조회한다() throws Exception {
		when(settlementService.getSettlement(1L, 2L, MemberRole.BIDDER)).thenReturn(settlement());

		mockMvc.perform(get("/api/settlements/1").principal(winner)
						.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.BIDDER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.totalAmount").value(140_000))
				.andExpect(jsonPath("$.paymentStatus").value("PENDING"));
	}

	@Test
	void 경매_ID로도_조회한다() throws Exception {
		when(settlementService.getSettlementByAuctionId(5L, 2L, null)).thenReturn(settlement());

		mockMvc.perform(get("/api/settlements").param("auctionId", "5").principal(winner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.auctionId").value(5));
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mockMvc.perform(get("/api/settlements/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SETTLEMENT_AUTH_REQUIRED"));
	}

	@Test
	void 당사자가_아니면_403() throws Exception {
		when(settlementService.getSettlement(1L, 2L, null)).thenThrow(new SettlementAccessDeniedException());

		mockMvc.perform(get("/api/settlements/1").principal(winner))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SETTLEMENT_FORBIDDEN"));
	}

	@Test
	void 없는_정산이면_404() throws Exception {
		when(settlementService.getSettlement(99L, 2L, null)).thenThrow(new SettlementNotFoundException());

		mockMvc.perform(get("/api/settlements/99").principal(winner))
				.andExpect(status().isNotFound());
	}

	@Test
	void 내_정산_목록을_조회한다() throws Exception {
		when(settlementService.getMySettlements(2L, PaymentStatus.PENDING)).thenReturn(List.of(settlement()));

		mockMvc.perform(get("/api/settlements/me").param("status", "PENDING").principal(winner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].paymentStatus").value("PENDING"));
	}

	@Test
	void 내_정산_목록은_로그인이_필요하다() throws Exception {
		mockMvc.perform(get("/api/settlements/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 잘못된_상태값이면_400() throws Exception {
		mockMvc.perform(get("/api/settlements/me").param("status", "WRONG").principal(winner))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}
}
