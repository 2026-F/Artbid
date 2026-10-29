package com.artbid.certificate.domain;

public enum CertificateStatus {
	ACTIVE,  // 유효
	REVOKED  // 재발급 등으로 폐기. 서명이 맞아도 유효한 보증서로 보지 않는다
}
