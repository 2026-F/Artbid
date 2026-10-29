package com.artbid.settlement.service;

import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 배치 1단계: 결제 기한이 지난 미결제 정산을 만료(FAILED) 처리한다.
 * 정산 하나당 트랜잭션 하나로 처리해서 한 건이 실패해도 나머지는 진행된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementExpiryService {

	private final SettlementRepository settlementRepository;
	private final PaymentRepository paymentRepository;

	public List<Long> findOverdueIds(LocalDateTime now) {
		return settlementRepository.findIdsByStatusAndDeadlineAtOrBefore(
				com.artbid.settlement.domain.PaymentStatus.PENDING, now);
	}

	/**
	 * 만료 처리했으면 true.
	 * 결과를 아직 모르는 결제(REQUESTED)가 있으면 만료하지 않는다. PG에서 실제로 승인됐을 수 있기 때문이다
	 * (결과 불명을 실패로 확정하지 않는다). 결제 결과가 정해진 뒤 다음 배치에서 다시 판단한다.
	 * 결제 시작도 같은 정산 행을 잠그고 기한을 확인하므로, 이 잠금 안에서는 새 결제가 끼어들 수 없다.
	 */
	@Transactional
	public boolean expireOne(Long settlementId, LocalDateTime now) {
		Settlement settlement = settlementRepository.findByIdForUpdate(settlementId).orElse(null);
		if (settlement == null) {
			return false;
		}
		if (paymentRepository.existsBySettlementIdAndStatusIn(settlementId, List.of(PaymentStatus.REQUESTED))) {
			log.info("[정산 배치] settlementId={} 결과 불명 결제가 있어 만료 보류", settlementId);
			return false;
		}
		return settlement.expire(now);
	}
}
