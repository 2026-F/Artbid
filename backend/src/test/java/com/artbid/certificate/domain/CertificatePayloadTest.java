package com.artbid.certificate.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePayloadTest {

	private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 10, 29, 12, 0, 0, 123_456_000);

	private CertificatePayload payload(String title, Double width) {
		return new CertificatePayload("ABC-20261029-0A1B2C3D", 7L, 3L, 2L, title, "캔버스에 유채",
				width, 100.0, null, 2024, "https://example.com/cert.pdf", ISSUED_AT, "v1");
	}

	@Test
	void 필드_순서가_고정된_문자열을_만든다() {
		String canonical = payload("붉은 정원", 80.0).canonical();

		assertThat(canonical).startsWith(CertificatePayload.FORMAT_VERSION + "\nserialNumber=ABC-20261029-0A1B2C3D\nartworkId=7\n");
		assertThat(canonical).contains("\nwidthCm=80\nheightCm=100\ndepthCm=\n"); // 60.0 → 60, 없는 값은 빈칸
		assertThat(canonical).contains("\nissuedAt=2026-10-29T12%3A00%3A00.123456\n"); // 값은 URL 인코딩
		assertThat(canonical).endsWith("\nkeyVersion=v1");
	}

	@Test
	void 같은_내용이면_항상_같은_문자열이다() {
		assertThat(payload("붉은 정원", 80.0).canonical()).isEqualTo(payload("붉은 정원", 80.00).canonical());
	}

	@Test
	void 내용이_바뀌면_문자열도_바뀐다() {
		assertThat(payload("붉은 정원", 80.0).canonical()).isNotEqualTo(payload("붉은 정원", 81.0).canonical());
		assertThat(payload("붉은 정원", 80.0).canonical()).isNotEqualTo(payload("붉은 정원 ", 80.0).canonical());
	}

	@Test
	void 제목에_줄바꿈을_넣어도_다른_필드처럼_보이지_않는다() {
		String canonical = payload("무제\nartworkId=999", 80.0).canonical();

		assertThat(canonical.lines().filter(line -> line.startsWith("artworkId="))).containsExactly("artworkId=7");
	}
}
