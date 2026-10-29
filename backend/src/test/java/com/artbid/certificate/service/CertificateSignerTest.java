package com.artbid.certificate.service;

import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificatePayload;
import com.artbid.certificate.domain.CertificateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateSignerTest {

	static final String SECRET = "test-only-certificate-secret-0123456789";

	static CertificatePayload payload(String title) {
		return new CertificatePayload("ABC-20261029-0A1B2C3D", 7L, 3L, 2L, title, "캔버스에 유채", 80.0, 100.0, null, 2024,
				null, LocalDateTime.of(2026, 10, 29, 12, 0), "v1");
	}

	@Test
	void 서명한_보증서_내용은_검증에_성공한다() {
		CertificateSigner signer = new CertificateSigner(SECRET, "v1");

		String signature = signer.sign(payload("붉은 정원"));

		assertThat(signer.verify(payload("붉은 정원"), signature)).isTrue();
		assertThat(signer.verify(payload("붉은 정원 (수정)"), signature)).isFalse();
	}

	@Test
	void 다른_키로_만든_서명은_검증에_실패한다() {
		String signature = new CertificateSigner(SECRET, "v1").sign(payload("붉은 정원"));

		assertThat(new CertificateSigner(SECRET + "-other", "v1").verify(payload("붉은 정원"), signature)).isFalse();
	}

	@Test
	void 키가_32바이트보다_짧으면_시작하지_않는다() {
		assertThatThrownBy(() -> new CertificateSigner("short-secret", "v1")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void toString에_키가_나오지_않는다() {
		assertThat(new CertificateSigner(SECRET, "v1").toString()).doesNotContain(SECRET);
	}

	@Test
	void 보증서는_저장된_내용으로_같은_서명_대상을_다시_만든다() {
		CertificateSigner signer = new CertificateSigner(SECRET, "v1");
		ArtworkCertificate certificate = ArtworkCertificate.issue(payload("붉은 정원"), signer.sign(payload("붉은 정원")));

		assertThat(certificate.payload()).isEqualTo(payload("붉은 정원"));
		assertThat(signer.verify(certificate.payload(), certificate.getSignature())).isTrue();
		assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
	}

	@Test
	void DB에서_내용을_고치면_저장된_서명과_맞지_않는다() {
		CertificateSigner signer = new CertificateSigner(SECRET, "v1");
		ArtworkCertificate certificate = ArtworkCertificate.issue(payload("붉은 정원"), signer.sign(payload("붉은 정원")));

		ReflectionTestUtils.setField(certificate, "productionYear", 1950); // 연도 조작

		assertThat(signer.verify(certificate.payload(), certificate.getSignature())).isFalse();
	}

	@Test
	void 폐기하면_REVOKED가_된다() {
		CertificateSigner signer = new CertificateSigner(SECRET, "v1");
		ArtworkCertificate certificate = ArtworkCertificate.issue(payload("붉은 정원"), signer.sign(payload("붉은 정원")));

		certificate.revoke(LocalDateTime.of(2026, 10, 30, 9, 0));

		assertThat(certificate.isActive()).isFalse();
		assertThat(certificate.getRevokedAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 9, 0));
	}
}
