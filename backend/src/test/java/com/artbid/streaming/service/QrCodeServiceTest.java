package com.artbid.streaming.service;

import com.artbid.auction.service.AuctionService;
import com.artbid.infra.qrcode.QrCodeGenerator;
import com.artbid.streaming.config.FrontendProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QrCodeServiceTest {

	private AuctionService auctionService;
	private QrCodeGenerator qrCodeGenerator;
	private FrontendProperties frontendProperties;
	private QrCodeService qrCodeService;

	@BeforeEach
	void setup() {
		auctionService = mock(AuctionService.class);
		qrCodeGenerator = mock(QrCodeGenerator.class);
		frontendProperties = new FrontendProperties();
		frontendProperties.setBaseUrl("https://artbid.example.com");
		qrCodeService = new QrCodeService(auctionService, qrCodeGenerator, frontendProperties);
	}

	@Test
	void 시청_페이지_주소를_QR로_인코딩한다() {
		when(qrCodeGenerator.generatePng("https://artbid.example.com/auctions/7/watch")).thenReturn(new byte[]{1, 2, 3});

		byte[] result = qrCodeService.generateWatchPageQrCode(7L);

		assertThat(result).containsExactly(1, 2, 3);
		verify(auctionService).getAuction(7L);
	}

	@Test
	void 없는_경매면_QR을_만들지_않고_예외를_그대로_전파한다() {
		when(auctionService.getAuction(99L)).thenThrow(new IllegalArgumentException("경매를 찾을 수 없습니다: 99"));

		assertThatThrownBy(() -> qrCodeService.generateWatchPageQrCode(99L))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(qrCodeGenerator);
	}
}
