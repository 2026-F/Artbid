package com.artbid.streaming.service;

import com.artbid.auction.service.AuctionService;
import com.artbid.infra.qrcode.QrCodeGenerator;
import com.artbid.streaming.config.FrontendProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 경매 고정 링크(/bid/{auctionId})를 QR코드로 내려준다. 오프라인 전시·현장에서 스캔해 입찰 페이지로 들어오는 용도. */
@Service
@RequiredArgsConstructor
public class QrCodeService {

	private final AuctionService auctionService;
	private final QrCodeGenerator qrCodeGenerator;
	private final FrontendProperties frontendProperties;

	public byte[] generateBidPageQrCode(Long auctionId) {
		auctionService.getAuction(auctionId); // 없는 경매면 IllegalArgumentException(404)

		String bidPageUrl = frontendProperties.getBaseUrl() + "/bid/" + auctionId;
		return qrCodeGenerator.generatePng(bidPageUrl);
	}
}
