package com.artbid.media.repository;

import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.TranscodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtworkMediaRepository extends JpaRepository<ArtworkMedia, Long> {

	List<ArtworkMedia> findByArtworkIdOrderBySortOrder(Long artworkId);

	// 한 번의 폴링에서 확인할 트랜스코딩 진행 중 미디어 (오래된 것부터)
	List<ArtworkMedia> findTop50ByTranscodeStatusOrderByIdAsc(TranscodeStatus transcodeStatus);
}
