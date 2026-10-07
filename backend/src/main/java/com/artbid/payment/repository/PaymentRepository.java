package com.artbid.payment.repository;

import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	boolean existsBySettlementIdAndStatusIn(Long settlementId, Collection<PaymentStatus> statuses);
}
