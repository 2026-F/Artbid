package com.artbid.media.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.infra.storage.S3PresignedUrlProvider;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.MediaType;
import com.artbid.media.dto.PresignedUploadResponse;
import com.artbid.media.exception.MediaAccessDeniedException;
import com.artbid.media.exception.MediaTypeConflictException;
import com.artbid.media.exception.MediaUploadTemporarilyUnavailableException;
import com.artbid.media.repository.ArtworkMediaRepository;
import com.artbid.media.service.TranscodeDispatcher.TranscodeRequested;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

	private static final String LOCK_TIMEOUT_SQL = "SET LOCAL lock_timeout = '3000ms'";

	private final ArtworkMediaRepository artworkMediaRepository;
	private final ArtworkRepository artworkRepository;
	private final S3PresignedUrlProvider s3PresignedUrlProvider;
	private final TranscodeService transcodeService;
	private final ApplicationEventPublisher events;
	private final EntityManager entityManager;

	// infra/storage의 S3PresignedUrlProvider를 사용해 업로드용 presigned URL 발급
	public PresignedUploadResponse issuePresignedUrl(Long artworkId, Long memberId, boolean admin, String fileName,
			String contentType) {
		requireUploadPermission(artworkId, memberId, admin);

		MediaType mediaType = MediaType.fromContentTypeAndFileName(contentType, fileName);
		String objectKey = buildObjectKey(artworkId, fileName);
		String uploadUrl = s3PresignedUrlProvider.issueUploadUrl(objectKey, contentType);

		return new PresignedUploadResponse(uploadUrl, objectKey, mediaType.name());
	}

	// 업로드 완료 콜백 처리 — 동영상이면 커밋 후 트랜스코딩을 요청한다.
	// mediaType은 presigned URL 발급 시 판별해 내려준 값을 클라이언트가 그대로 돌려보낸다.
	// (완료 시점에는 contentType을 알 수 없어 objectKey만으로는 PHOTO/VIDEO를 구분할 수 없음 — #11)
	@Transactional
	public void completeUpload(Long artworkId, Long memberId, boolean admin, String objectKey, String mediaTypeValue) {
		// 소유자 ID만 먼저 읽어 권한 없는 요청을 락 대기 전에 거절한다.
		Long consignorId = artworkRepository.findConsignorIdById(artworkId)
				.orElseThrow(() -> artworkNotFound(artworkId));
		checkUploadPermission(consignorId, memberId, admin);
		// 이 작품용으로 발급한 경로의 객체만 등록한다. 다른 작품이나 임의 경로의 객체를 연결하지 못하게 막는다.
		if (objectKey == null || !objectKey.startsWith(objectKeyPrefix(artworkId)) || objectKey.contains("..")) {
			throw new MediaAccessDeniedException("이 작품에 발급된 업로드 경로가 아닙니다.");
		}

		MediaType mediaType = MediaType.fromName(mediaTypeValue);
		Artwork artwork = lockArtwork(artworkId);
		checkUploadPermission(artwork.getConsignorId(), memberId, admin);
		// 작품 행 락을 커밋까지 유지해 완료 재시도와 같은 작품의 동시 등록을 직렬화한다.
		var existing = artworkMediaRepository.findByArtworkIdAndObjectKey(artworkId, objectKey);
		if (existing.isPresent()) {
			requireSameMediaType(existing.get(), mediaType);
			return;
		}

		String objectUrl = s3PresignedUrlProvider.issuePublicUrl(objectKey);
		for (ArtworkMedia legacy : artworkMediaRepository.findByArtworkIdAndObjectKeyIsNullOrderBySortOrderAscIdAsc(artworkId)) {
			String originalUrl = legacy.getSourceUrl() != null ? legacy.getSourceUrl() : legacy.getUrl();
			if (matchesLegacyObject(originalUrl, objectUrl, objectKey, artworkId)) {
				requireSameMediaType(legacy, mediaType);
				artworkMediaRepository.fillLegacyObjectKey(legacy.getId(), objectKey);
				return;
			}
		}

		int sortOrder = artworkMediaRepository.countByArtworkId(artworkId);

		if (mediaType == MediaType.VIDEO) {
			ArtworkMedia media = new ArtworkMedia(artworkId, mediaType, null, objectUrl, sortOrder, objectKey);
			boolean transcodeEnabled = transcodeService.isEnabled();
			if (transcodeEnabled) {
				media.queueTranscode();
			} else {
				media.skipTranscode();
			}
			artworkMediaRepository.save(media);
			if (transcodeEnabled) {
				events.publishEvent(new TranscodeRequested(media.getId()));
			}
			return;
		}

		// 사진 / 3D 모델(glTF·GLB)은 트랜스코딩이 필요 없어 업로드 즉시 감상 가능
		ArtworkMedia media = new ArtworkMedia(artworkId, mediaType, objectUrl, null, sortOrder, objectKey);
		artworkMediaRepository.save(media);
	}

	private boolean matchesLegacyObject(String originalUrl, String objectUrl, String objectKey, Long artworkId) {
		if (originalUrl == null) {
			return false;
		}
		if (originalUrl.equals(objectUrl)) {
			return true;
		}
		try {
			String path = URI.create(originalUrl).getRawPath();
			int start = path == null ? -1 : path.indexOf("/" + objectKeyPrefix(artworkId));
			return start >= 0 && path.substring(start + 1).equals(objectKey);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private void requireSameMediaType(ArtworkMedia existing, MediaType requestedType) {
		if (existing.getMediaType() != requestedType) {
			throw new MediaTypeConflictException();
		}
	}

	public List<ArtworkMedia> getMediaList(Long artworkId) {
		return artworkMediaRepository.findByArtworkIdOrderBySortOrder(artworkId);
	}

	// 작품을 등록한 위탁자 본인이나 관리자만 업로드할 수 있다.
	private void requireUploadPermission(Long artworkId, Long memberId, boolean admin) {
		Artwork artwork = artworkRepository.findById(artworkId)
				.orElseThrow(() -> artworkNotFound(artworkId));
		checkUploadPermission(artwork.getConsignorId(), memberId, admin);
	}

	private Artwork lockArtwork(Long artworkId) {
		entityManager.createNativeQuery(LOCK_TIMEOUT_SQL).executeUpdate();
		try {
			return artworkRepository.findByIdForUpdate(artworkId)
					.orElseThrow(() -> artworkNotFound(artworkId));
		} catch (PessimisticLockingFailureException e) {
			throw new MediaUploadTemporarilyUnavailableException(e);
		}
	}

	private IllegalArgumentException artworkNotFound(Long artworkId) {
		return new IllegalArgumentException("작품을 찾을 수 없습니다: " + artworkId);
	}

	private void checkUploadPermission(Long consignorId, Long memberId, boolean admin) {
		if (!admin && !Objects.equals(consignorId, memberId)) {
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
