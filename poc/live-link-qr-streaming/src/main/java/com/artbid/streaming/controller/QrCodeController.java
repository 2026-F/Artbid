package com.artbid.streaming.controller;

import com.artbid.auction.service.BidService;
import com.artbid.config.AppProperties;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 입찰 페이지 고정 링크를 인코딩한 QR코드 이미지(PNG)를 내려준다.
 * GET /qrcode/{itemId}       -> image/png
 * GET /qrcode/{itemId}/link  -> 실제로 인코딩된 URL 텍스트 (디버그/확인용)
 */
@RestController
@RequiredArgsConstructor
public class QrCodeController {

    private final AppProperties appProperties;
    private final BidService bidService;

    @GetMapping(value = "/qrcode/{itemId}", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qrCode(@PathVariable String itemId, HttpServletRequest request) {
        bidService.findItem(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 itemId: " + itemId));

        String link = buildBidLink(itemId, request);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(link, BarcodeFormat.QR_CODE, 320, 320);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "QR 생성 실패", e);
        }
    }

    @GetMapping(value = "/qrcode/{itemId}/link", produces = MediaType.TEXT_PLAIN_VALUE)
    public String qrLink(@PathVariable String itemId, HttpServletRequest request) {
        bidService.findItem(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 itemId: " + itemId));
        return buildBidLink(itemId, request);
    }

    private String buildBidLink(String itemId, HttpServletRequest request) {
        String base = appProperties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/bid/" + itemId;
    }
}
