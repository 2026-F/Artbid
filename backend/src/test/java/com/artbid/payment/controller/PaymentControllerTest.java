package com.artbid.payment.controller;

import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.auth.JwtTokenProvider;
import com.artbid.member.domain.MemberRole;
import com.artbid.payment.domain.*;
import com.artbid.payment.dto.ActivePaymentResponse;
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
import java.time.OffsetDateTime;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {
	private static final String JWT_TEST_KEY = "payment-controller-tests-only-signing-key";
	private static final String ACTIVE_URL = "/api/settlements/2/payments/active";
	private static final OffsetDateTime DEADLINE = OffsetDateTime.parse("2026-11-28T12:00:00+09:00");
	private static final OffsetDateTime SERVER_NOW = OffsetDateTime.parse("2026-11-25T12:00:00.123456+09:00");
	private PaymentService service;
	private MockMvc mvc;
	private JwtTokenProvider tokens;

	@BeforeEach
	void setup() {
		service = mock(PaymentService.class);
		tokens = new JwtTokenProvider(JWT_TEST_KEY, 60_000);
		mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
				.addFilters(new JwtAuthenticationFilter(tokens))
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

	@Test
	void 활성_결제_조회는_검증된_토큰을_사용하고_응답을_캐시하지_않는다() throws Exception {
		when(service.getActive(2L, 3L)).thenReturn(new ActivePaymentResponse(
				com.artbid.settlement.domain.PaymentStatus.PENDING, null, DEADLINE, SERVER_NOW,
				new ActivePaymentResponse.ActivePayment(1L, PaymentStatus.REQUESTED)));
		mvc.perform(get(ACTIVE_URL).header("Authorization", "Bearer " + tokens.generateToken(3L, MemberRole.BIDDER)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.settlementStatus").value("PENDING"))
				.andExpect(jsonPath("$.paymentDeadlineAt").value("2026-11-28T12:00:00+09:00"))
				.andExpect(jsonPath("$.serverNow").value("2026-11-25T12:00:00.123456+09:00"))
				.andExpect(jsonPath("$.payment.id").value(1))
				.andExpect(jsonPath("$.payment.status").value("REQUESTED"))
				.andExpect(jsonPath("$.payment.amount").doesNotExist());
	}

	@Test
	void 활성_결제가_없으면_payment_필드를_null로_반환한다() throws Exception {
		when(service.getActive(2L, 3L)).thenReturn(new ActivePaymentResponse(
				com.artbid.settlement.domain.PaymentStatus.PAID, null, DEADLINE, SERVER_NOW, null));
		mvc.perform(get(ACTIVE_URL).principal(() -> "3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.settlementStatus").value("PAID"))
				.andExpect(jsonPath("$.payment").hasJsonPath())
				.andExpect(jsonPath("$.payment").value(nullValue()));
	}

	@Test
	void 활성_결제_조회에서_비로그인과_만료_토큰은_401이다() throws Exception {
		String expired = new JwtTokenProvider(JWT_TEST_KEY, -60_000).generateToken(3L, MemberRole.BIDDER);
		mvc.perform(get(ACTIVE_URL).param("memberId", "3"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mvc.perform(get(ACTIVE_URL).header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		verifyNoInteractions(service);
	}

	@Test
	void 활성_결제_조회에서_다른_구매자와_판매자는_403이고_없는_정산은_404다() throws Exception {
		when(service.getActive(2L, 4L)).thenThrow(new PaymentException(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한 없음"));
		when(service.getActive(2L, 3L)).thenThrow(new PaymentException(HttpStatus.NOT_FOUND, "NOT_FOUND", "정산 없음"));
		for (MemberRole role : new MemberRole[]{MemberRole.BIDDER, MemberRole.CONSIGNOR}) {
			mvc.perform(get(ACTIVE_URL).header("Authorization", "Bearer " + tokens.generateToken(4L, role)))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		mvc.perform(get(ACTIVE_URL).principal(() -> "3"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void TOSS_승인_요청을_서비스로_전달한다() throws Exception {
		when(service.confirmToss(1L, 3L, "pk_abc", "payment_1")).thenReturn(response(PaymentStatus.PAID));
		mvc.perform(post("/api/payments/1/confirm").principal(() -> "3")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"paymentKey\":\"pk_abc\",\"orderId\":\"payment_1\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
	}

	@Test
	void 인증_없이_승인_요청하면_401이다() throws Exception {
		mvc.perform(post("/api/payments/1/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"paymentKey\":\"pk_abc\",\"orderId\":\"payment_1\"}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
}
