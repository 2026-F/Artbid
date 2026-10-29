package com.artbid.certificate.service;

import com.artbid.certificate.domain.CertificatePayload;
import com.artbid.common.util.HmacUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 보증서 서명 키를 들고 서명·검증한다. 키는 설정(환경변수 CERTIFICATE_HMAC_SECRET)으로만 받고 로그에 남기지 않는다.
 * 키가 32바이트보다 짧으면 서버가 시작되지 않게 한다(HMAC-SHA256 권장 길이).
 * keyVersion은 나중에 키를 바꿀 때 어떤 키로 서명했는지 구분하려고 보증서에 함께 저장한다.
 * 지금은 현재 키 하나만 쓰므로, 키를 바꾸면 이전 버전 키도 함께 보관해서 검증하는 기능이 필요하다(후속).
 */
@Component
public class CertificateSigner {

	static final int MIN_KEY_BYTES = 32;

	private final byte[] key;
	private final String keyVersion;

	public CertificateSigner(@Value("${app.certificate.hmac-secret}") String secret,
			@Value("${app.certificate.key-version:v1}") String keyVersion) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
			throw new IllegalStateException("app.certificate.hmac-secret는 " + MIN_KEY_BYTES + "바이트 이상이어야 합니다.");
		}
		if (keyVersion == null || keyVersion.isBlank() || keyVersion.length() > 20) {
			throw new IllegalStateException("app.certificate.key-version을 확인해주세요.");
		}
		this.key = secret.getBytes(StandardCharsets.UTF_8);
		this.keyVersion = keyVersion;
	}

	public String keyVersion() {
		return keyVersion;
	}

	public String sign(CertificatePayload payload) {
		return HmacUtil.sign(key, payload.canonical());
	}

	public boolean verify(CertificatePayload payload, String signature) {
		return HmacUtil.verify(key, payload.canonical(), signature);
	}

	@Override
	public String toString() {
		return "CertificateSigner[keyVersion=" + keyVersion + "]"; // 키는 출력하지 않는다
	}
}
