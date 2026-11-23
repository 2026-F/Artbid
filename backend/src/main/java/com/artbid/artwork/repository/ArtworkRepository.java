package com.artbid.artwork.repository;

import com.artbid.artwork.domain.Artwork;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArtworkRepository extends JpaRepository<Artwork, Long>, JpaSpecificationExecutor<Artwork> {
	Optional<Artwork> findByIdAndConsignorId(Long id, Long consignorId);

	Page<Artwork> findByConsignorId(Long consignorId, Pageable pageable);

	@Query("select artwork.consignorId from Artwork artwork where artwork.id = :id")
	Optional<Long> findConsignorIdById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select artwork from Artwork artwork where artwork.id = :id")
	Optional<Artwork> findByIdForUpdate(@Param("id") Long id);
}
