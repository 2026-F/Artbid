package com.artbid.certificate.repository;

import com.artbid.certificate.domain.ArtworkCertificate;
import com.artbid.certificate.domain.CertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtworkCertificateRepository extends JpaRepository<ArtworkCertificate, Long> {

	Optional<ArtworkCertificate> findBySerialNumber(String serialNumber);

	List<ArtworkCertificate> findByArtworkIdAndStatus(Long artworkId, CertificateStatus status);
}
