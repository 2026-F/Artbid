package com.artbid.certificate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 작품 보증서. 발급 시점의 작품 정보를 그대로 저장(스냅샷)하고 그 내용에 HMAC 서명을 붙인다.
 * 작품 정보가 나중에 바뀌어도 보증서 내용은 바뀌지 않는다. 바뀐 정보로 보증서가 필요하면 재발급한다.
 * DB에서 내용을 직접 고치면 저장된 서명과 맞지 않게 되어 검증에서 걸린다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_artwork_certificate_serial", columnNames = "serialNumber"),
		indexes = @Index(name = "idx_artwork_certificate_artwork", columnList = "artworkId"))
public class ArtworkCertificate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 40)
	private String serialNumber;

	private Long artworkId;
	private Long artistId;
	private Long consignorId;

	@Column(length = 100)
	private String title;
	@Column(length = 100)
	private String medium;
	private Double widthCm;
	private Double heightCm;
	private Double depthCm;
	private Integer productionYear;
	@Column(length = 500)
	private String certificateUrl;

	private LocalDateTime issuedAt;
	@Column(length = 20)
	private String keyVersion;
	@Column(length = 100)
	private String signature;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private CertificateStatus status;
	private LocalDateTime revokedAt;

	public static ArtworkCertificate issue(CertificatePayload payload, String signature) {
		if (payload == null || payload.serialNumber() == null || payload.artworkId() == null
				|| payload.issuedAt() == null || signature == null || signature.isBlank()) {
			throw new IllegalArgumentException("보증서 일련번호, 작품, 발급 시각, 서명은 필수입니다.");
		}
		ArtworkCertificate certificate = new ArtworkCertificate();
		certificate.serialNumber = payload.serialNumber();
		certificate.artworkId = payload.artworkId();
		certificate.artistId = payload.artistId();
		certificate.consignorId = payload.consignorId();
		certificate.title = payload.title();
		certificate.medium = payload.medium();
		certificate.widthCm = payload.widthCm();
		certificate.heightCm = payload.heightCm();
		certificate.depthCm = payload.depthCm();
		certificate.productionYear = payload.productionYear();
		certificate.certificateUrl = payload.certificateUrl();
		certificate.issuedAt = payload.issuedAt();
		certificate.keyVersion = payload.keyVersion();
		certificate.signature = signature;
		certificate.status = CertificateStatus.ACTIVE;
		return certificate;
	}

	/** 저장된 내용으로 서명 대상을 다시 만든다. 검증할 때 이 값으로 서명을 다시 계산해 비교한다. */
	public CertificatePayload payload() {
		return new CertificatePayload(serialNumber, artworkId, artistId, consignorId, title, medium,
				widthCm, heightCm, depthCm, productionYear, certificateUrl, issuedAt, keyVersion);
	}

	public boolean isActive() {
		return status == CertificateStatus.ACTIVE;
	}

	public void revoke(LocalDateTime now) {
		if (status == CertificateStatus.ACTIVE) {
			status = CertificateStatus.REVOKED;
			revokedAt = now;
		}
	}
}
