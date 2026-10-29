package com.artbid.shipping.repository;

import com.artbid.shipping.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

	Optional<Shipment> findBySettlementId(Long settlementId);
}
