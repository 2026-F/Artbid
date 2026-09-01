package com.artbid.artwork.repository;

import com.artbid.artwork.domain.Artwork;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtworkRepository extends JpaRepository<Artwork, Long> {
}
