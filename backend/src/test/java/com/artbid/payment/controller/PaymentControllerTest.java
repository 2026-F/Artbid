package com.artbid.payment.controller;

import com.artbid.payment.domain.*;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.exception.PaymentException;
import com.artbid.payment.service.PaymentService;
import com.artbid.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {
	private PaymentService service;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		service = mock(PaymentService.class);
		mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
				.setControllerAdvice(new GlobalExceptionHandler(), new PaymentExceptionHandler()).build();
	}

	private PaymentResponse response(PaymentStatus status) {
		return new PaymentResponse(1L, 2L, 965_000L, PaymentMethod.MOCK_PG, status,
				null, null, LocalDateTime.now(), null);
	}

	@Test
	void 검증된_Principal과_method로_결제한다() throws Exception {
		when(service.pay(2L, 3L, PaymentMethod.MOCK_PG)).thenReturn(response(PaymentStatus.PAID));
		mvc.perform(post("/api/settlements/2/payments").principal(() -> "3")
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"MOCK_PG\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.amount").value(965000));
		verify(service).pay(2L, 3L, PaymentMethod.MOCK_PG);
	}

	@Test
	void 결과_불명이면_조회할_ID와_202를_반환한다() throws Exception {
		when(service.pay(2L, 3L, PaymentMethod.MOCK_PG)).thenReturn(response(PaymentStatus.REQUESTED));
		mvc.perform(post("/api/settlements/2/payments").principal(() -> "3")
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"MOCK_PG\"}"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(1));
	}

	@Test
	void 인증_없는_요청과_조회는_401이다() throws Exception {
		mvc.perform(post("/api/settlements/2/payments")
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"MOCK_PG\",\"memberId\":3}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/payments/1")).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void 잘못된_method와_누락된_method는_400이다() throws Exception {
		for (String body : new String[]{"{}", "{\"method\":\"CARD\"}", "{\"amount\":100}"}) {
			mvc.perform(post("/api/settlements/2/payments").principal(() -> "3")
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(service);
	}

	@Test
	void 본인_결제_조회와_타인_접근_거절을_반영한다() throws Exception {
		when(service.get(1L, 3L)).thenReturn(response(PaymentStatus.PAID));
		when(service.get(1L, 4L)).thenThrow(new PaymentException(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한 없음"));
		mvc.perform(get("/api/payments/1").principal(() -> "3"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
		mvc.perform(get("/api/payments/1").principal(() -> "4"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}
}
