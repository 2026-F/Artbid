package com.artbid.infra.storage;

import org.springframework.stereotype.Component;

@Component
public class S3PresignedUrlProvider {
	// TODO: AWS SDK v2 S3Presigner로 업로드용 presigned URL 발급
	// 사진/동영상/3D 모델(glTF/GLB) 공통으로 사용
	public String issueUploadUrl(String objectKey, String contentType) {
		throw new UnsupportedOperationException("TODO: S3Presigner 연동 구현");
	}
}
