package com.artbid.infra.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 문자열(URL)을 PNG 이미지 바이트로 인코딩하는 QR코드 생성기. */
@Component
public class QrCodeGenerator {

	private static final int DEFAULT_SIZE_PX = 300;

	public byte[] generatePng(String content) {
		return generatePng(content, DEFAULT_SIZE_PX);
	}

	public byte[] generatePng(String content, int sizePx) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(matrix, "PNG", out);
			return out.toByteArray();
		} catch (WriterException | IOException e) {
			throw new QrCodeGenerationException(content, e);
		}
	}
}
