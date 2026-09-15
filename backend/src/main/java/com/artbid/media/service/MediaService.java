package com.artbid.media.service;

import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.infra.media.MediaConvertClient;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.MediaType;
import com.artbid.media.dto.PresignedUploadResponse;
import com.artbid.media.repository.ArtworkMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

	private static final String TRANSCODE_OUTPUT_PREFIX_FORMAT = "artworks/%d/media/transcoded/";

	private final ArtworkMediaRepository artworkMediaRepository;
	private final ArtworkRepository artworkRepository;
	private final S3PresignedUrlProvider s3PresignedUrlProvider;
	private final MediaConvertClient mediaConvertClient;

	// infra/storage의 S3PresignedUrlProvider를 사용해 업로드용 presigned URL 발급
	public PresignedUploadResponse issuePresignedUrl(Long artworkId, String fileName, String contentType) {
		requireArtworkExists(artworkId);

		MediaType mediaType = MediaType.fromContentTypeAndFileName(contentType, fileName);
		String objectKey = buildObjectKey(artworkId, fileName);
		String uploadUrl = s3PresignedUrlProvider.issueUploadUrl(objectKey, contentType);

		return new PresignedUploadResponse(uploadUrl, objectKey, mediaType.name());
	}

	// 업로드 완료 콜백 처리 — 동영상이면 infra/media의 MediaConvert 트랜스코딩 요청까지 트리거
	public void completeUpload(Long artworkId, String objectKey) {
		requireArtworkExists(artworkId);

		MediaType mediaType = MediaType.fromContentTypeAndFileName(null, objectKey);
		String objectUrl = s3PresignedUrlProvider.issuePublicUrl(objectKey);
		int sortOrder = artworkMediaRepository.findByArtworkIdOrderBySortOrder(artworkId).size();

		if (mediaType == MediaType.VIDEO) {
			// 트랜스코딩 완료 전까지는 sourceUrl만 채우고 url은 비워둔다.
			// (job 완료를 감지해서 url을 채우는 부분은 MediaConvertClient의 TODO 참고)
			ArtworkMedia media = new ArtworkMedia(artworkId, mediaType, null, objectUrl, sortOrder);
			artworkMediaRepository.save(media);
			mediaConvertClient.requestTranscode(objectKey, TRANSCODE_OUTPUT_PREFIX_FORMAT.formatted(artworkId));
			return;
		}

		// 사진 / 3D 모델(glTF·GLB)은 트랜스코딩이 필요 없어 업로드 즉시 감상 가능
		ArtworkMedia media = new ArtworkMedia(artworkId, mediaType, objectUrl, null, sortOrder);
		artworkMediaRepository.save(media);
	}

	public List<ArtworkMedia> getMediaList(Long artworkId) {
		return artworkMediaRepository.findByArtworkIdOrderBySortOrder(artworkId);
	}

	private void requireArtworkExists(Long artworkId) {
		if (!artworkRepository.existsById(artworkId)) {
			throw new IllegalArgumentException("작품을 찾을 수 없습니다: " + artworkId);
		}
	}

	private String buildObjectKey(Long artworkId, String fileName) {
		String extension = extractExtension(fileName);
		String key = "artworks/%d/media/%s".formatted(artworkId, UUID.randomUUID());
		return extension.isEmpty() ? key : key + "." + extension;
	}

	private String extractExtension(String fileName) {
		if (fileName == null) {
			return "";
		}
		int dot = fileName.lastIndexOf('.');
		return dot == -1 ? "" : fileName.substring(dot + 1).toLowerCase();
	}
}
