package com.artbid.media.repository;

import com.artbid.media.domain.ArtworkMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtworkMediaRepository extends JpaRepository<ArtworkMedia, Long> {

	List<ArtworkMedia> findByArtworkIdOrderBySortOrder(Long artworkId);
}
