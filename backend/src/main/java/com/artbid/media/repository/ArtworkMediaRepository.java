package com.artbid.media.repository;

import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.TranscodeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArtworkMediaRepository extends JpaRepository<ArtworkMedia, Long> {

	List<ArtworkMedia> findByArtworkIdOrderBySortOrder(Long artworkId);

	Optional<ArtworkMedia> findByArtworkIdAndObjectKey(Long artworkId, String objectKey);

	List<ArtworkMedia> findByArtworkIdAndObjectKeyIsNullOrderBySortOrderAscIdAsc(Long artworkId);

	int countByArtworkId(Long artworkId);

	// Poller가 이전 스냅샷을 저장할 때 키가 지워지지 않도록 일반 엔티티 갱신과 분리한다.
	@Modifying
	@Query("update ArtworkMedia media set media.objectKey = :key where media.id = :id and media.objectKey is null")
	void fillLegacyObjectKey(@Param("id") Long id, @Param("key") String objectKey);

	List<ArtworkMedia> findByArtworkIdInOrderBySortOrderAscIdAsc(java.util.Collection<Long> artworkIds);

	List<ArtworkMedia> findTop50ByTranscodeStatusAndTranscodeJobIdIsNotNullOrderByIdAsc(TranscodeStatus status);

	@Query("""
			select media.id from ArtworkMedia media
			where media.transcodeStatus = :status
			  and media.objectKey is not null and media.transcodeJobId is null
			  and (media.transcodeClaimUntil is null or media.transcodeClaimUntil <= :now)
			order by media.id
			""")
	List<Long> findPendingTranscodeIds(@Param("status") TranscodeStatus status, @Param("now") Instant now,
			Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select media from ArtworkMedia media where media.id = :id")
	Optional<ArtworkMedia> findByIdForUpdate(@Param("id") Long id);
}
