package com.artbid.media.dto;

/**
 * 업로드용 presigned URL 발급 응답.
 * objectKey는 업로드 완료 후 /complete 호출 시 그대로 다시 보내야 한다.
 */
public record PresignedUploadResponse(String uploadUrl, String objectKey, String mediaType) {
}
