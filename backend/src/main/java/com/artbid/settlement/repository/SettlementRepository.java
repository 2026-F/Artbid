package com.artbid.settlement.repository;

import com.artbid.settlement.domain.PaymentStatus;
import com.artbid.settlement.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Settlement s where s.id = :id")
	Optional<Settlement> findByIdForUpdate(@Param("id") Long id);

	Optional<Settlement> findByAuctionId(Long auctionId);

	// 정산 배치: 결제 기한이 지난 미결제 정산 ID
	@Query("select s.id from Settlement s where s.paymentStatus = :status and s.paymentDeadlineAt <= :now order by s.id")
	List<Long> findIdsByStatusAndDeadlineAtOrBefore(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now);
}
