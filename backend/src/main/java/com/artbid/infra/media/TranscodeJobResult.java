package com.artbid.infra.media;

/**
 * MediaConvert job 조회 결과를 우리 쪽에서 필요한 형태로 단순화한 값.
 */
public record TranscodeJobResult(Status status, String errorMessage) {

	public enum Status {
		IN_PROGRESS, // SUBMITTED, PROGRESSING
		COMPLETE,
		FAILED       // ERROR, CANCELED, job 없음
	}

	public static TranscodeJobResult inProgress() {
		return new TranscodeJobResult(Status.IN_PROGRESS, null);
	}

	public static TranscodeJobResult complete() {
		return new TranscodeJobResult(Status.COMPLETE, null);
	}

	public static TranscodeJobResult failed(String errorMessage) {
		return new TranscodeJobResult(Status.FAILED, errorMessage);
	}
}
