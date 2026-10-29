package com.artbid.shipping.controller;

import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.shipping.domain.Shipment;
import com.artbid.shipping.domain.ShippingAddress;
import com.artbid.shipping.exception.ShipmentException;
import com.artbid.shipping.service.ShipmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShipmentControllerTest {

	@Mock
	ShipmentService shipmentService;

	MockMvc mockMvc;
	final Principal winner = () -> "2";

	static final String VALID_BODY = """
			{"recipientName":"김수령","recipientPhone":"010-1234-5678","zipCode":"04524",
			 "address":"서울 중구 세종대로 110","addressDetail":"3층","requestMemo":"문 앞"}
			""";

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ShipmentController(shipmentService))
				.setControllerAdvice(new ShipmentExceptionHandler())
				.setValidator(validator)
				.setMessageConverters(
						new StringHttpMessageConverter(StandardCharsets.UTF_8),
						new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	static Shipment shipment() {
		Shipment shipment = Shipment.create(1L,
				new ShippingAddress("김수령", "010-1234-5678", "04524", "서울 중구 세종대로 110", "3층", "문 앞"),
				LocalDateTime.of(2026, 10, 29, 12, 0));
		ReflectionTestUtils.setField(shipment, "id", 10L);
		return shipment;
	}

	@Test
	void 배송지를_입력한다() throws Exception {
		when(shipmentService.saveAddress(eq(1L), eq(2L), any(ShippingAddress.class))).thenReturn(shipment());

		mockMvc.perform(put("/api/settlements/1/shipment").principal(winner)
						.contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(10))
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.zipCode").value("04524"));
	}

	@Test
	void 우편번호_형식이_틀리면_400이고_입력값을_되돌려주지_않는다() throws Exception {
		String body = VALID_BODY.replace("04524", "4524");

		mockMvc.perform(put("/api/settlements/1/shipment").principal(winner)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("zipCode 값을 확인해주세요."))
				.andExpect(content().string(not(containsString("세종대로"))));
	}

	@Test
	void 연락처_형식이_틀리면_400() throws Exception {
		mockMvc.perform(put("/api/settlements/1/shipment").principal(winner)
						.contentType(MediaType.APPLICATION_JSON).content(VALID_BODY.replace("010-1234-5678", "abc")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("recipientPhone 값을 확인해주세요."));
	}

	@Test
	void 서비스_오류는_상태코드와_오류코드로_내려준다() throws Exception {
		when(shipmentService.saveAddress(eq(1L), eq(2L), any(ShippingAddress.class)))
				.thenThrow(new ShipmentException(HttpStatus.FORBIDDEN, "SHIPMENT_FORBIDDEN", "낙찰자 본인만 배송지를 입력할 수 있습니다."));

		mockMvc.perform(put("/api/settlements/1/shipment").principal(winner)
						.contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SHIPMENT_FORBIDDEN"));
	}

	@Test
	void 배송_정보를_조회한다() throws Exception {
		when(shipmentService.get(1L, 3L, MemberRole.CONSIGNOR)).thenReturn(shipment());

		mockMvc.perform(get("/api/settlements/1/shipment").principal(() -> "3")
						.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recipientName").value("김수령"))
				.andExpect(jsonPath("$.address").value("서울 중구 세종대로 110"))
				.andExpect(jsonPath("$.trackingNumber").doesNotExist());
	}

	@Test
	void 로그인하지_않으면_서비스가_401로_거절한다() throws Exception {
		when(shipmentService.get(1L, null, null))
				.thenThrow(new ShipmentException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다."));

		mockMvc.perform(get("/api/settlements/1/shipment"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	void 운송장을_등록한다() throws Exception {
		Shipment shipped = shipment();
		shipped.registerTracking("CJ대한통운", "123456789012", LocalDateTime.of(2026, 10, 30, 9, 0));
		when(shipmentService.registerTracking(1L, 3L, MemberRole.CONSIGNOR, "CJ대한통운", "123456789012")).thenReturn(shipped);

		mockMvc.perform(put("/api/settlements/1/shipment/tracking").principal(() -> "3")
						.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"123456789012\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SHIPPED"))
				.andExpect(jsonPath("$.carrier").value("CJ대한통운"))
				.andExpect(jsonPath("$.trackingNumber").value("123456789012"));
	}

	@Test
	void 운송장_번호_형식이_틀리면_400() throws Exception {
		mockMvc.perform(put("/api/settlements/1/shipment/tracking").principal(() -> "3")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"12 34\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("trackingNumber 값을 확인해주세요."));
	}
}
