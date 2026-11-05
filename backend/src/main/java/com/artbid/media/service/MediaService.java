package com.artbid.media.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.MediaType;
import com.artbid.media.dto.PresignedUploadResponse;
import com.artbid.media.exception.MediaAccessDeniedException;
import com.artbid.media.repository.ArtworkMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

	private final ArtworkMediaRepository artworkMediaRepository;
	private final ArtworkRepository artworkRepository;
	private final S3PresignedUrlProvider s3PresignedUrlProvider;
	private final TranscodeService transcodeService;

	// infra/storage의 S3PresignedUrlProvider를 사용해 업로드용 presigned URL 발급
	public PresignedUploadResponse issuePresignedUrl(Long artworkId, Long memberId, boolean admin, String fileName,
			String contentType) {
		requireUploadPermission(artworkId, memberId, admin);

		MediaType mediaType = MediaType.fromContentTypeAndFileName(contentType, fileName);
		String objectKey = buildObjectKey(artworkId, fileName);
		String uploadUrl = s3PresignedUrlProvider.issueUploadUrl(objectKey, contentType);

		return new PresignedUploadResponse(uploadUrl, objectKey, mediaType.name());
	}

	// 업로드 완료 콜백 처리 — 동영상이면 MediaConvert 트랜스코딩 요청까지 트리거
	// mediaType은 presigned URL 발급 시 판별해 내려준 값을 클라이언트가 그대로 돌려보낸다.
	// (완료 시점에는 contentType을 알 수 없어 objectKey만으로는 PHOTO/VIDEO를 구분할 수 없음 — #11)
	public void completeUpload(Long artworkId, Long memberId, boolean admin, String objectKey, String mediaTypeValue) {
		requireUploadPermission(artworkId, memberId, admin);
		// 이 작품용으로 발급한 경로의 객체만 등록한다. 다른 작품이나 임의 경로의 객체를 연결하지 못하게 막는다.
		if (objectKey == null || !objectKey.startsWith(objectKeyPrefix(artworkId)) || objectKey.contains("..")) {
			throw new MediaAccessDeniedException("이 작품에 발급된 업로드 경로가 아닙니다.");
		}

		MediaType mediaType = MediaType.fromName(mediaTypeValue);
		String objectUrl = s3PresignedUrlProvider.issuePublicUrl(objectKey);
		int sortOrder = artworkMediaRepository.findByArtworkIdOrderBySortOrder(artworkId).size();

		if (mediaType == MediaType.VIDEO) {
			// 트랜스코딩 완료 전까지는 sourceUrl만 채우고 url은 비워둔다.
			// (완료 감지는 TranscodeStatusPoller, MediaConvert 비활성화 시에는 원본을 바로 url로 사용)
			ArtworkMedia media = new ArtworkMedia(artworkId, mediaType, null, objectUrl, sortOrder);
			transcodeService.start(media, objectKey);
			artworkMediaRepository.save(media);
			return;
		}

		// 사진 / 3D 모델(glTF·GLB)은 트랜스코딩이 필요 없어 업로드 즉시 감상 가능
		ArtworkMedia media = new ArtworkMedia(artworkId, mediaType, objectUrl, null, sortOrder);
		artworkMediaRepository.save(media);
	}

	public List<ArtworkMedia> getMediaList(Long artworkId) {
		return artworkMediaRepository.findByArtworkIdOrderBySortOrder(artworkId);
	}

	// 작품을 등록한 위탁자 본인이나 관리자만 업로드할 수 있다.
	private void requireUploadPermission(Long artworkId, Long memberId, boolean admin) {
		Artwork artwork = artworkRepository.findById(artworkId)
				.orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다: " + artworkId));
		if (!admin && !Objects.equals(artwork.getConsignorId(), memberId)) {
			throw new MediaAccessDeniedException("작품을 등록한 위탁자만 미디어를 올릴 수 있습니다.");
		}
	}

	private String objectKeyPrefix(Long artworkId) {
		return "artworks/%d/media/".formatted(artworkId);
	}

	private String buildObjectKey(Long artworkId, String fileName) {
		String extension = extractExtension(fileName);
		String key = objectKeyPrefix(artworkId) + UUID.randomUUID();
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
