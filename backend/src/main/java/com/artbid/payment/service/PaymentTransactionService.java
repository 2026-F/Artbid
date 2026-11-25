package com.artbid.payment.service;

import com.artbid.notification.domain.NotificationType;
import com.artbid.notification.service.NotificationRequestedEvent;
import com.artbid.payment.domain.Payment;
import com.artbid.payment.domain.PaymentMethod;
import com.artbid.payment.domain.PaymentStatus;
import com.artbid.payment.dto.ActivePaymentResponse;
import com.artbid.payment.dto.PaymentResponse;
import com.artbid.payment.exception.PaymentException;
import com.artbid.payment.gateway.PaymentGateway;
import com.artbid.payment.repository.PaymentRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {
	private final SettlementRepository settlementRepository;
	private final PaymentRepository paymentRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock paymentClock;

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
		apply(settlement, payment, result);
		return PaymentResponse.from(payment);
	}

	/**
	 * 복구 스케줄러용. 결과 불명(REQUESTED) 결제를 PG 조회 결과로 확정한다. 확정했으면 true.
	 *
	 * 정산 행을 먼저 잠근 뒤 결제를 읽는다. 같은 순간 승인 요청(complete)이 진행 중이었다면
	 * 그쪽이 끝날 때까지 기다렸다가 결과를 보고, 이미 확정됐으면 건드리지 않는다.
	 * complete()도 정산 → 결제 순서로 잠그므로 잠금 순서가 같아 교착 상태가 생기지 않는다.
	 */
	@Transactional
	public boolean resolveUnknown(Long paymentId, PaymentGateway.Result result) {
		Long settlementId = paymentRepository.findSettlementIdById(paymentId)
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "결제 내역이 없습니다."));
		Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "정산 내역이 없습니다."));
		Payment payment = findPayment(paymentId); // 잠금을 잡은 뒤 처음 읽으므로 최신 상태다
		if (payment.getStatus() != PaymentStatus.REQUESTED) {
			return false; // 그사이 승인 요청이 먼저 확정함
		}
		apply(settlement, payment, result);
		return true;
	}

	// 승인이면 결제와 정산을 완료로, 거절이면 결제만 실패로 바꾼다. (정산은 재결제할 수 있게 PENDING 유지)
	private void apply(Settlement settlement, Payment payment, PaymentGateway.Result result) {
		if (result.approved()) {
			boolean firstPaid = settlement.getPaymentStatus() != com.artbid.settlement.domain.PaymentStatus.PAID;
			payment.markPaid(result.transactionId(), databaseTime());
			settlement.markPaid();
			if (firstPaid) {
				notifyPaid(settlement);
			}
		} else {
			payment.markFailed(result.failureReason());
		}
	}

	@Transactional(readOnly = true)
	public PaymentResponse get(Long paymentId, Long memberId) {
		Payment payment = findPayment(paymentId);
		Settlement settlement = settlementRepository.findById(payment.getSettlementId())
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "정산 내역이 없습니다."));
		checkOwner(settlement, memberId);
		return PaymentResponse.from(payment);
	}

	// 승인 커밋이 두 SELECT 사이에 일어나도 정산과 결제를 같은 스냅샷에서 읽는다.
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public ActivePaymentResponse getActive(Long settlementId, Long memberId) {
		Settlement settlement = settlementRepository.findById(settlementId)
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "정산 내역이 없습니다."));
		checkOwner(settlement, memberId);
		ActivePaymentResponse.ActivePayment active = paymentRepository
				.findFirstBySettlementIdAndStatusOrderByIdDesc(settlementId, PaymentStatus.REQUESTED)
				.map(payment -> new ActivePaymentResponse.ActivePayment(payment.getId(), payment.getStatus()))
				.orElse(null);
		return new ActivePaymentResponse(settlement.getPaymentStatus(), offsetTime(settlement.getExpiredAt()),
				offsetTime(settlement.getPaymentDeadlineAt()), offsetTime(databaseTime()), active);
	}

	// 결제 완료 알림. 이 트랜잭션이 커밋된 뒤에 만들어지고, 실패해도 결제에는 영향이 없다(NotificationEventListener).
	private void notifyPaid(Settlement settlement) {
		eventPublisher.publishEvent(new NotificationRequestedEvent(
				settlement.getWinnerId(),
				NotificationType.PAYMENT_COMPLETED,
				"결제가 완료되었습니다",
				"낙찰 작품 결제가 완료되었습니다. 배송지를 입력해 주세요.",
				"/mypage/orders"));
	}

	private Payment findPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new PaymentException(NOT_FOUND, "NOT_FOUND", "결제 내역이 없습니다."));
	}

	private LocalDateTime databaseTime() {
		// PostgreSQL timestamp 정밀도에 맞춰 최초 응답과 재조회 값이 같도록 한다.
		return LocalDateTime.now(paymentClock).truncatedTo(ChronoUnit.MICROS);
	}

	private OffsetDateTime offsetTime(LocalDateTime time) {
		return time == null ? null : time.atZone(paymentClock.getZone()).toOffsetDateTime();
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
