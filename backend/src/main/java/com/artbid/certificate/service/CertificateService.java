package com.artbid.certificate.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificatePayload;
import com.artbid.certificate.domain.CertificateStatus;
import com.artbid.certificate.dto.CertificateVerifyResponse;
import com.artbid.certificate.dto.CertificateVerifyResponse.Result;
import com.artbid.certificate.exception.CertificateException;
import com.artbid.certificate.repository.ArtworkCertificateRepository;
import com.artbid.member.domain.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class CertificateService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final DateTimeFormatter SERIAL_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private final ArtworkCertificateRepository certificateRepository;
	private final CertificateSigner signer;

	/**
	 * 보증서 발급. 작품 위탁자 본인이나 관리자만 할 수 있다.
	 * 지금 작품 정보를 스냅샷으로 저장하고 서명한다. 이미 유효한 보증서가 있으면 폐기하고 새로 발급한다(재발급).
	 */
	@Transactional
	public ArtworkCertificate issue(Long artworkId, Long memberId, MemberRole role) {
		requireLogin(memberId);
		Artwork artwork = certificateRepository.findArtworkForUpdate(artworkId)
				.orElseThrow(() -> new CertificateException(NOT_FOUND, "ARTWORK_NOT_FOUND", "작품을 찾을 수 없습니다."));
		if (role != MemberRole.ADMIN && !Objects.equals(artwork.getConsignorId(), memberId)) {
			throw new CertificateException(FORBIDDEN, "CERTIFICATE_FORBIDDEN", "작품 위탁자나 관리자만 보증서를 발급할 수 있습니다.");
		}

		LocalDateTime now = now();
		certificateRepository.findByArtworkIdAndStatus(artworkId, CertificateStatus.ACTIVE)
				.forEach(previous -> previous.revoke(now));

		CertificatePayload payload = new CertificatePayload(
				newSerialNumber(now), artwork.getId(), artwork.getArtistId(), artwork.getConsignorId(),
				artwork.getTitle(), artwork.getMedium(), artwork.getWidthCm(), artwork.getHeightCm(), artwork.getDepthCm(),
				artwork.getProductionYear(), artwork.getCertificateUrl(), now, signer.keyVersion());
		return certificateRepository.save(ArtworkCertificate.issue(payload, signer.sign(payload)));
	}

	/** 작품의 현재 유효한 보증서. 작품 상세 화면에서 보증서와 검증 링크(QR)를 보여줄 때 쓴다. 공개. */
	@Transactional(readOnly = true)
	public ArtworkCertificate getActive(Long artworkId) {
		return certificateRepository.findByArtworkIdAndStatus(artworkId, CertificateStatus.ACTIVE).stream()
				.findFirst()
				.orElseThrow(() -> new CertificateException(NOT_FOUND, "CERTIFICATE_NOT_FOUND", "발급된 보증서가 없습니다."));
	}

	/**
	 * 보증서 진위 확인. 로그인 없이 누구나 할 수 있다(구매자가 보증서의 일련번호와 서명으로 확인).
	 * 저장된 내용으로 서명을 다시 계산해서 두 가지를 확인한다.
	 * 1) 입력한 서명이 맞는지: 보증서 사본의 서명이 위조되거나 잘못 옮겨 적히지 않았는지
	 * 2) 저장된 서명이 맞는지: DB의 보증서 내용이 발급 뒤에 바뀌지 않았는지
	 */
	@Transactional(readOnly = true)
	public CertificateVerifyResponse verify(String serialNumber, String signature) {
		if (signature == null || signature.isBlank()) {
			throw new CertificateException(BAD_REQUEST, "VALIDATION_ERROR", "서명 값을 입력해주세요.");
		}
		ArtworkCertificate certificate = certificateRepository.findBySerialNumber(serialNumber).orElse(null);
		if (certificate == null) {
			return CertificateVerifyResponse.of(Result.NOT_FOUND, null);
		}
		boolean providedMatches = signer.verify(certificate.payload(), signature);
		boolean storedMatches = signer.verify(certificate.payload(), certificate.getSignature());
		if (!providedMatches || !storedMatches) {
			return CertificateVerifyResponse.of(Result.SIGNATURE_MISMATCH, certificate);
		}
		return CertificateVerifyResponse.of(certificate.isActive() ? Result.VALID : Result.REVOKED, certificate);
	}

	// 예: ART-20261029-3F9A1C7B. 날짜 + 추측하기 어려운 무작위 8자리
	static String newSerialNumber(LocalDateTime now) {
		byte[] random = new byte[4];
		RANDOM.nextBytes(random);
		return "ART-" + now.format(SERIAL_DATE) + "-" + HexFormat.of().withUpperCase().formatHex(random);
	}

	static void requireLogin(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new CertificateException(UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
		}
	}

	static LocalDateTime now() {
		return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
	}
}
