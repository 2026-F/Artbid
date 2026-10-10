package com.artbid.streaming.controller;

import com.artbid.streaming.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions/{auctionId}/qrcode")
@RequiredArgsConstructor
public class QrCodeController {

	private final QrCodeService qrCodeService;

	/** 이 경매의 고정 링크(/bid/{auctionId})를 가리키는 QR코드 PNG. */
	@GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
	public byte[] getQrCode(@PathVariable Long auctionId) {
		return qrCodeService.generateBidPageQrCode(auctionId);
	}
}
