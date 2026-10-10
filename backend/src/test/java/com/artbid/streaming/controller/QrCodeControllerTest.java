package com.artbid.streaming.controller;

import com.artbid.common.exception.GlobalExceptionHandler;
import com.artbid.streaming.service.QrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QrCodeControllerTest {

	private QrCodeService qrCodeService;
	private MockMvc mvc;

	@BeforeEach
	void setup() {
		qrCodeService = mock(QrCodeService.class);
		mvc = MockMvcBuilders.standaloneSetup(new QrCodeController(qrCodeService))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void QR코드를_PNG로_내려준다() throws Exception {
		byte[] png = {1, 2, 3};
		when(qrCodeService.generateBidPageQrCode(7L)).thenReturn(png);

		mvc.perform(get("/api/auctions/7/qrcode"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(png));
	}

	@Test
	void 없는_경매면_404() throws Exception {
		when(qrCodeService.generateBidPageQrCode(99L))
				.thenThrow(new IllegalArgumentException("경매를 찾을 수 없습니다: 99"));

		mvc.perform(get("/api/auctions/99/qrcode"))
				.andExpect(status().isNotFound());
	}
}
