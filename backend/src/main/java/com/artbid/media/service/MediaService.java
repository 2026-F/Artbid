package com.artbid.media.service;

import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.repository.ArtworkMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaService {

	private final ArtworkMediaRepository artworkMediaRepository;

	// TODO: infra/storage의 S3PresignedUrlProvider를 사용해 업로드용 presigned URL 발급
	public String issuePresignedUrl(Long artworkId, String fileName, String contentType) {
		throw new UnsupportedOperationException("TODO: S3 presigned URL 발급 구현");
	}

	// TODO: 업로드 완료 콜백 처리 — 동영상이면 infra/media의 MediaConvert 트랜스코딩 요청까지 트리거
	public void completeUpload(Long artworkId, String objectKey) {
		throw new UnsupportedOperationException("TODO: 업로드 완료 처리 구현");
	}

	public List<ArtworkMedia> getMediaList(Long artworkId) {
		return artworkMediaRepository.findByArtworkIdOrderBySortOrder(artworkId);
	}
}
