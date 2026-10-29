package com.artbid.certificate.dto;

import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificateStatus;

import java.time.LocalDateTime;

/**
 * 보증서 응답. 공개 검증 화면에도 쓰므로 위탁자 회원 ID는 내보내지 않는다(서명 대상에는 포함).
 * 화면은 serialNumber와 signature로 검증 링크나 QR을 만들 수 있다.
 */
public record CertificateResponse(
		String serialNumber,
		Long artworkId,
		Long artistId,
		String title,
		String medium,
		Double widthCm,
		Double heightCm,
		Double depthCm,
		Integer productionYear,
		String certificateUrl,
		LocalDateTime issuedAt,
		String keyVersion,
		String signature,
		CertificateStatus status,
		LocalDateTime revokedAt) {

	public static CertificateResponse from(ArtworkCertificate certificate) {
		return new CertificateResponse(
				certificate.getSerialNumber(),
				certificate.getArtworkId(),
				certificate.getArtistId(),
				certificate.getTitle(),
				certificate.getMedium(),
				certificate.getWidthCm(),
				certificate.getHeightCm(),
				certificate.getDepthCm(),
				certificate.getProductionYear(),
				certificate.getCertificateUrl(),
				certificate.getIssuedAt(),
				certificate.getKeyVersion(),
				certificate.getSignature(),
				certificate.getStatus(),
				certificate.getRevokedAt());
	}
}
