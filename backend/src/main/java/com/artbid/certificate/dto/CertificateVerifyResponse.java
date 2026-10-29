package com.artbid.certificate.dto;

import com.artbid.certificate.domain.ArtworkCertificate;

/**
 * 보증서 진위 확인 결과.
 * result
 * - VALID: 서버가 발급한 원본과 내용, 서명이 모두 일치하고 유효하다
 * - REVOKED: 서명은 맞지만 재발급 등으로 폐기된 보증서다
 * - SIGNATURE_MISMATCH: 서명이 틀렸다(위조, 오타, 또는 저장된 내용이 변조됨)
 * - NOT_FOUND: 그런 일련번호로 발급된 보증서가 없다
 * certificate는 VALID, REVOKED일 때만 담는다. 서명이 틀린 경우에는 내용을 보여주지 않는다.
 */
public record CertificateVerifyResponse(boolean valid, Result result, CertificateResponse certificate) {

	public enum Result {
		VALID, REVOKED, SIGNATURE_MISMATCH, NOT_FOUND
	}

	public static CertificateVerifyResponse of(Result result, ArtworkCertificate certificate) {
		boolean showContents = result == Result.VALID || result == Result.REVOKED;
		return new CertificateVerifyResponse(
				result == Result.VALID,
				result,
				showContents ? CertificateResponse.from(certificate) : null);
	}
}
