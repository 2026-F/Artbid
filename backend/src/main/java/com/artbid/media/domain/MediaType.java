package com.artbid.media.domain;

public enum MediaType {
	PHOTO, VIDEO, MODEL_3D;

	/**
	 * 업로드 요청의 Content-Type/파일명으로 미디어 종류를 판별한다.
	 * glTF/GLB는 브라우저·OS마다 Content-Type을 다르게 보내는 경우가 많아 확장자도 함께 본다.
	 */
	public static MediaType fromContentTypeAndFileName(String contentType, String fileName) {
		String lowerName = fileName == null ? "" : fileName.toLowerCase();

		if (lowerName.endsWith(".glb") || lowerName.endsWith(".gltf")
				|| "model/gltf-binary".equalsIgnoreCase(contentType)
				|| "model/gltf+json".equalsIgnoreCase(contentType)) {
			return MODEL_3D;
		}
		if (contentType != null && contentType.startsWith("video/")) {
			return VIDEO;
		}
		if (contentType != null && contentType.startsWith("image/")) {
			return PHOTO;
		}

		throw new IllegalArgumentException("지원하지 않는 파일 형식입니다 (contentType=%s, fileName=%s)"
				.formatted(contentType, fileName));
	}

	/**
	 * /presigned-url 응답으로 내려준 mediaType 문자열(PHOTO / VIDEO / MODEL_3D)을 enum으로 변환한다.
	 */
	public static MediaType fromName(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("mediaType이 비어 있습니다");
		}
		try {
			return MediaType.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("지원하지 않는 mediaType입니다: " + value);
		}
	}
}
