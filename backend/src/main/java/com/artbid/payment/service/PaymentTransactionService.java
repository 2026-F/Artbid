package com.artbid.payment.service;

import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.exception.PaymentException;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {
	private final SettlementRepository settlementRepository;
	private final PaymentRepository paymentRepository;

	@Transactional
	public PaymentResponse start(Long settlementId, Long memberId, PaymentMethod method) {
		if (method == null) {
			throw new PaymentException(BAD_REQUEST, "VALIDATION_ERROR", "결제 수단은 필수입니다.");
		}
		Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "정산 내역이 없습니다."));
		checkOwner(settlement, memberId);
		LocalDateTime now = databaseTime();
		if (!settlement.isPayableAt(now)) {
			throw new PaymentException(CONFLICT, "SETTLEMENT_NOT_PAYABLE", "결제 가능한 정산이 아닙니다.");
		}
		if (paymentRepository.existsBySettlementIdAndStatusIn(settlementId,
				List.of(PaymentStatus.REQUESTED, PaymentStatus.PAID))) {
			throw new PaymentException(CONFLICT, "PAYMENT_ALREADY_EXISTS", "진행 중이거나 완료된 결제가 있습니다.");
		}
		Payment payment = paymentRepository.save(Payment.request(settlementId,
				settlement.getTotalAmount(), method, now));
		return PaymentResponse.from(payment);
	}

	@Transactional
	public PaymentResponse complete(Long settlementId, Long paymentId, PaymentGateway.Result result) {
		Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "정산 내역이 없습니다."));
		Payment payment = findPayment(paymentId);
		if (!settlementId.equals(payment.getSettlementId())) {
			throw new PaymentException(CONFLICT, "PAYMENT_SETTLEMENT_MISMATCH", "결제와 정산이 일치하지 않습니다.");
		}
		if (result.approved()) {
			payment.markPaid(result.transactionId(), databaseTime());
			settlement.markPaid();
		} else {
			payment.markFailed(result.failureReason());
			// 개별 시도만 실패 처리한다. Settlement는 재시도 가능한 PENDING을 유지한다.
		}
		return PaymentResponse.from(payment);
	}

	@Transactional(readOnly = true)
	public PaymentResponse get(Long paymentId, Long memberId) {
		Payment payment = findPayment(paymentId);
		Settlement settlement = settlementRepository.findById(payment.getSettlementId())
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "정산 내역이 없습니다."));
		checkOwner(settlement, memberId);
		return PaymentResponse.from(payment);
	}

	private Payment findPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "결제 내역이 없습니다."));
	}

	private LocalDateTime databaseTime() {
		// PostgreSQL timestamp 정밀도에 맞춰 최초 응답과 재조회 값이 같도록 한다.
		return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
	}

	private void checkOwner(Settlement settlement, Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new PaymentException(UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
		}
		if (!memberId.equals(settlement.getWinnerId())) {
			throw new PaymentException(FORBIDDEN, "FORBIDDEN", "낙찰자만 결제 내역에 접근할 수 있습니다.");
		}
	}
}
