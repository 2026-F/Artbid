package com.artbid.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * HMAC-SHA256 서명 생성과 검증. 감정서·인증서(작품 보증서) 위변조 방지에 쓴다.
 *
 * - 서명은 URL에 그대로 넣을 수 있도록 Base64 URL-safe(패딩 없음)로 인코딩한다.
 * - 검증은 MessageDigest.isEqual로 비교한다. 문자열 equals는 앞에서부터 다른 글자가 나오는 순간 멈춰서
 *   비교 시간 차이로 서명을 한 글자씩 추측하는 공격(타이밍 공격)에 약하다.
 * - 키와 서명 대상 원문은 로그에 남기지 않는다.
 */
public final class HmacUtil {

	private static final String ALGORITHM = "HmacSHA256";

	private HmacUtil() {
	}

	public static String sign(byte[] key, String message) {
		if (key == null || key.length == 0) {
			throw new IllegalArgumentException("HMAC 키가 비어 있습니다.");
		}
		if (message == null) {
			throw new IllegalArgumentException("서명할 내용이 없습니다.");
		}
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (GeneralSecurityException e) {
			// HmacSHA256은 모든 JDK에 있어야 하는 알고리즘이라 정상 환경에서는 발생하지 않는다
			throw new IllegalStateException("HMAC 서명 생성에 실패했습니다.", e);
		}
	}

	/** 서명이 맞으면 true. 서명이 비었거나 형식이 틀려도 예외 없이 false. */
	public static boolean verify(byte[] key, String message, String signature) {
		if (signature == null || signature.isBlank()) {
			return false;
		}
		byte[] expected = sign(key, message).getBytes(StandardCharsets.US_ASCII);
		byte[] actual = signature.trim().getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expected, actual);
	}
}
