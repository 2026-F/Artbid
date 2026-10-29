package com.artbid.certificate.controller;

import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificatePayload;
import com.artbid.certificate.exception.CertificateException;
import com.artbid.certificate.service.CertificateService;
import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CertificateControllerTest {

	@Mock
	CertificateService certificateService;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		mockMvc = MockMvcBuilders.standaloneSetup(new CertificateController(certificateService))
				.setControllerAdvice(new CertificateExceptionHandler())
				.setMessageConverters(
						new StringHttpMessageConverter(StandardCharsets.UTF_8),
						new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	static ArtworkCertificate certificate() {
		return ArtworkCertificate.issue(new CertificatePayload("ABC-20261029-0A1B2C3D", 7L, 3L, 2L, "붉은 정원", "캔버스에 유채",
				80.0, 100.0, null, 2024, null, LocalDateTime.of(2026, 10, 29, 12, 0), "v1"), "signature-value");
	}

	@Test
	void 보증서를_발급한다() throws Exception {
		when(certificateService.issue(7L, 2L, MemberRole.CONSIGNOR)).thenReturn(certificate());

		mockMvc.perform(post("/api/artworks/7/certificates").principal(() -> "2")
						.requestAttr(JwtAuthenticationFilter.ROLE_ATTRIBUTE, MemberRole.CONSIGNOR))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.serialNumber").value("ABC-20261029-0A1B2C3D"))
				.andExpect(jsonPath("$.signature").value("signature-value"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.consignorId").doesNotExist());
	}

	@Test
	void 권한이_없으면_403() throws Exception {
		when(certificateService.issue(7L, 9L, null))
				.thenThrow(new CertificateException(HttpStatus.FORBIDDEN, "CERTIFICATE_FORBIDDEN", "권한 없음"));

		mockMvc.perform(post("/api/artworks/7/certificates").principal(() -> "9"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CERTIFICATE_FORBIDDEN"));
	}
}
