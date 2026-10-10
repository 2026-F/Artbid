package com.artbid.settlement.repository;

import com.artbid.settlement.domain.Settlement;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Settlement a where s.id = id")
    Optional<Settlement> findByIdForUpdate(@Param("id") Long id);
    Optional<Settlement> findByAuctionId(Long auctionId);

}
