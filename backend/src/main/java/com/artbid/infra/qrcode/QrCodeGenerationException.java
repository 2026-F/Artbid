package com.artbid.infra.qrcode;

/** ZXing 인코딩 자체가 실패한 경우(잘못된 입력 등). GlobalExceptionHandler가 500으로 처리한다. */
public class QrCodeGenerationException extends RuntimeException {

	public QrCodeGenerationException(String content, Throwable cause) {
		super("QR코드를 생성하지 못했습니다: content=" + content, cause);
	}
}
