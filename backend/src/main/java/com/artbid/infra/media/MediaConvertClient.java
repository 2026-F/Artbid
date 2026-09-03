package com.artbid.infra.media;

import org.springframework.stereotype.Component;

@Component
public class MediaConvertClient {
	// TODO: AWS MediaConvert 트랜스코딩 job 요청 (원본 동영상 -> 스트리밍용 포맷)
	public void requestTranscode(String sourceObjectKey, String outputPrefix) {
		throw new UnsupportedOperationException("TODO: MediaConvert job 요청 구현");
	}
}
