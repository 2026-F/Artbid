package com.artbid.payment.repository;

import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	boolean existsBySettlementIdAndStatusIn(Long settlementId, Collection<PaymentStatus> statuses);

	Optional<Payment> findFirstBySettlementIdAndStatusOrderByIdDesc(Long settlementId, PaymentStatus status);

	// 복구 대상: 요청된 지 오래된 결과 불명 결제. 한 번에 50건씩만 처리한다.
	List<Payment> findTop50ByStatusAndRequestedAtBeforeOrderByIdAsc(PaymentStatus status, LocalDateTime before);

	// 결제 엔티티를 읽지 않고 정산 ID만 가져온다. 정산 행을 잠근 "뒤에" 결제를 처음 읽어야 최신 상태를 볼 수 있기 때문이다.
	@Query("select p.settlementId from Payment p where p.id = :id")
	Optional<Long> findSettlementIdById(@Param("id") Long id);
}
