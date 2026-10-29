package com.artbid.settlement.repository;

import com.artbid.settlement.domain.SellerPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {

	boolean existsBySettlementId(Long settlementId);

	List<SellerPayout> findByConsignorIdOrderByIdDesc(Long consignorId);
}
