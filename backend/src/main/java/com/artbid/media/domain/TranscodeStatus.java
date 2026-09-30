package com.artbid.media.domain;

/**
 * 동영상 트랜스코딩 진행 상태. 사진·3D 모델은 트랜스코딩 대상이 아니라 null이다.
 */
public enum TranscodeStatus {
	PROCESSING, // MediaConvert job 진행 중 — url은 비어 있고 sourceUrl만 있음
	COMPLETED,  // 스트리밍용(HLS) 출력이 준비됨 — url에 출력 URL
	FAILED,     // job 실패 — 원본(sourceUrl)으로라도 재생되도록 url에 원본 URL
	SKIPPED     // MediaConvert 비활성화(로컬 개발 등) — url에 원본 URL
}
