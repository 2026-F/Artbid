package com.artbid.certificate.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificateStatus;
import com.artbid.certificate.dto.CertificateVerifyResponse;
import com.artbid.certificate.dto.CertificateVerifyResponse.Result;
import com.artbid.certificate.exception.CertificateException;
import com.artbid.certificate.repository.ArtworkCertificateRepository;
import com.artbid.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

	static final Long ARTWORK_ID = 7L;
	static final Long CONSIGNOR_ID = 2L;

	@Mock
	ArtworkCertificateRepository certificateRepository;

	CertificateSigner signer = new CertificateSigner(CertificateSignerTest.SECRET, "v1");
	CertificateService service;

	@BeforeEach
	void setUp() {
		service = new CertificateService(certificateRepository, signer);
	}

	static Artwork artwork() {
		Artwork artwork = Artwork.builder()
				.consignorId(CONSIGNOR_ID).artistId(3L).title("붉은 정원의 오후").category(ArtworkCategory.PAINTING)
				.medium("캔버스에 유채").widthCm(80.0).heightCm(100.0).productionYear(2024).startPrice(1_000_000L)
				.build();
		ReflectionTestUtils.setField(artwork, "id", ARTWORK_ID);
		return artwork;
	}

	static void assertError(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(CertificateException.class, e -> {
			assertThat(e.getStatus()).isEqualTo(status);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	@Test
	void 위탁자가_발급하면_작품_정보를_담아_서명한다() {
		when(certificateRepository.findArtworkForUpdate(ARTWORK_ID)).thenReturn(Optional.of(artwork()));
		when(certificateRepository.findByArtworkIdAndStatus(ARTWORK_ID, CertificateStatus.ACTIVE)).thenReturn(List.of());
		when(certificateRepository.save(any(ArtworkCertificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ArtworkCertificate certificate = service.issue(ARTWORK_ID, CONSIGNOR_ID, MemberRole.CONSIGNOR);

		assertThat(certificate.getSerialNumber()).matches("ART-\\d{8}-[0-9A-F]{8}");
		assertThat(certificate.getTitle()).isEqualTo("붉은 정원의 오후");
		assertThat(certificate.getProductionYear()).isEqualTo(2024);
		assertThat(certificate.getKeyVersion()).isEqualTo("v1");
		assertThat(signer.verify(certificate.payload(), certificate.getSignature())).isTrue();
	}

	@Test
	void 재발급하면_이전_보증서는_폐기된다() {
		ArtworkCertificate previous = ArtworkCertificate.issue(CertificateSignerTest.payload("이전"), "sig");
		when(certificateRepository.findArtworkForUpdate(ARTWORK_ID)).thenReturn(Optional.of(artwork()));
		when(certificateRepository.findByArtworkIdAndStatus(ARTWORK_ID, CertificateStatus.ACTIVE)).thenReturn(List.of(previous));
		when(certificateRepository.save(any(ArtworkCertificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.issue(ARTWORK_ID, CONSIGNOR_ID, MemberRole.CONSIGNOR);

		assertThat(previous.getStatus()).isEqualTo(CertificateStatus.REVOKED);
		assertThat(previous.getRevokedAt()).isNotNull();
	}

	@Test
	void 관리자도_발급할_수_있다() {
		when(certificateRepository.findArtworkForUpdate(ARTWORK_ID)).thenReturn(Optional.of(artwork()));
		when(certificateRepository.findByArtworkIdAndStatus(ARTWORK_ID, CertificateStatus.ACTIVE)).thenReturn(List.of());
		when(certificateRepository.save(any(ArtworkCertificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.issue(ARTWORK_ID, 99L, MemberRole.ADMIN).isActive()).isTrue();
	}

	@Test
	void 위탁자가_아니면_발급할_수_없다() {
		when(certificateRepository.findArtworkForUpdate(ARTWORK_ID)).thenReturn(Optional.of(artwork()));

		assertError(() -> service.issue(ARTWORK_ID, 99L, MemberRole.CONSIGNOR), HttpStatus.FORBIDDEN, "CERTIFICATE_FORBIDDEN");
	}

	@Test
	void 작품이_없으면_404() {
		when(certificateRepository.findArtworkForUpdate(ARTWORK_ID)).thenReturn(Optional.empty());

		assertError(() -> service.issue(ARTWORK_ID, CONSIGNOR_ID, MemberRole.CONSIGNOR), HttpStatus.NOT_FOUND, "ARTWORK_NOT_FOUND");
	}

	@Test
	void 로그인하지_않으면_401() {
		assertError(() -> service.issue(ARTWORK_ID, null, null), HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
	}

	@Test
	void 일련번호는_매번_다르다() {
		LocalDateTime now = LocalDateTime.now();
		assertThat(CertificateService.newSerialNumber(now)).isNotEqualTo(CertificateService.newSerialNumber(now));
	}

	ArtworkCertificate issued() {
		var payload = CertificateSignerTest.payload("붉은 정원");
		return ArtworkCertificate.issue(payload, signer.sign(payload));
	}

	@Test
	void 일련번호와_서명이_맞으면_유효하다() {
		ArtworkCertificate certificate = issued();
		when(certificateRepository.findBySerialNumber(certificate.getSerialNumber())).thenReturn(Optional.of(certificate));

		CertificateVerifyResponse response = service.verify(certificate.getSerialNumber(), certificate.getSignature());

		assertThat(response.valid()).isTrue();
		assertThat(response.result()).isEqualTo(Result.VALID);
		assertThat(response.certificate().title()).isEqualTo("붉은 정원");
	}

	@Test
	void 서명이_틀리면_내용을_보여주지_않는다() {
		ArtworkCertificate certificate = issued();
		when(certificateRepository.findBySerialNumber(certificate.getSerialNumber())).thenReturn(Optional.of(certificate));

		CertificateVerifyResponse response = service.verify(certificate.getSerialNumber(), "forged-signature");

		assertThat(response.valid()).isFalse();
		assertThat(response.result()).isEqualTo(Result.SIGNATURE_MISMATCH);
		assertThat(response.certificate()).isNull();
	}

	@Test
	void 저장된_내용이_바뀌었으면_원래_서명으로도_실패한다() {
		ArtworkCertificate certificate = issued();
		String originalSignature = certificate.getSignature();
		ReflectionTestUtils.setField(certificate, "title", "다른 작품"); // DB 직접 변조
		when(certificateRepository.findBySerialNumber(certificate.getSerialNumber())).thenReturn(Optional.of(certificate));

		assertThat(service.verify(certificate.getSerialNumber(), originalSignature).result()).isEqualTo(Result.SIGNATURE_MISMATCH);
	}

	@Test
	void 폐기된_보증서는_유효하지_않다() {
		ArtworkCertificate certificate = issued();
		certificate.revoke(LocalDateTime.now());
		when(certificateRepository.findBySerialNumber(certificate.getSerialNumber())).thenReturn(Optional.of(certificate));

		CertificateVerifyResponse response = service.verify(certificate.getSerialNumber(), certificate.getSignature());

		assertThat(response.valid()).isFalse();
		assertThat(response.result()).isEqualTo(Result.REVOKED);
		assertThat(response.certificate().status()).isEqualTo(CertificateStatus.REVOKED);
	}

	@Test
	void 없는_일련번호는_NOT_FOUND() {
		when(certificateRepository.findBySerialNumber("ART-00000000-00000000")).thenReturn(Optional.empty());

		assertThat(service.verify("ART-00000000-00000000", "sig").result()).isEqualTo(Result.NOT_FOUND);
	}

	@Test
	void 서명을_안_넣으면_400() {
		assertError(() -> service.verify("ART-00000000-00000000", " "), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
	}
}
