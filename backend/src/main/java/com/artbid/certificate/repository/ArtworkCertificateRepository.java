package com.artbid.certificate.repository;

import com.artbid.artwork.domain.Artwork;
import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArtworkCertificateRepository extends JpaRepository<ArtworkCertificate, Long> {

	Optional<ArtworkCertificate> findBySerialNumber(String serialNumber);

	List<ArtworkCertificate> findByArtworkIdAndStatus(Long artworkId, CertificateStatus status);

	// 보증서 발급 중 작품 행을 잠근다. 같은 작품에 동시에 발급해도 유효(ACTIVE) 보증서가 두 개 생기지 않게 하기 위함.
	// 작품 패키지를 고치지 않으려고 여기에 둔다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Artwork a where a.id = :id")
	Optional<Artwork> findArtworkForUpdate(@Param("id") Long artworkId);
}
