package com.artbid.streaming.service;

import com.artbid.auction.service.AuctionService;
import com.artbid.infra.qrcode.QrCodeGenerator;
import com.artbid.streaming.config.FrontendProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 경매 라이브 시청 페이지(/auctions/{auctionId}/watch)를 QR코드로 내려준다.
 * 오프라인 전시·현장에서 스캔해 바로 시청 페이지로 들어오는 용도.
 * 프론트 라우트는 /bid/{itemId}가 아니라 /auctions/{id}/watch다(frontend 저장소 실제 구조 확인, 2026-10-10).
 */
@Service
@RequiredArgsConstructor
public class QrCodeService {

	private final AuctionService auctionService;
	private final QrCodeGenerator qrCodeGenerator;
	private final FrontendProperties frontendProperties;

	public byte[] generateWatchPageQrCode(Long auctionId) {
		auctionService.getAuction(auctionId); // 없는 경매면 IllegalArgumentException(404)

		String watchPageUrl = frontendProperties.getBaseUrl() + "/auctions/" + auctionId + "/watch";
		return qrCodeGenerator.generatePng(watchPageUrl);
	}
}
