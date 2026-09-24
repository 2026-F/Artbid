package com.artbid.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long settlementId;
	private Long amount;
	@Enumerated(EnumType.STRING)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	private PaymentStatus status;

	@Column(length = 100)
	private String transactionId;
	@Column(length = 500)
	private String failureReason;
	private LocalDateTime requestedAt;
	private LocalDateTime paidAt;

	/** amount는 클라이언트 입력이 아니라 서버에 저장된 정산 금액을 전달한다. */
	public static Payment request(Long settlementId, Long amount, PaymentMethod method,
			LocalDateTime requestedAt) {
		if (settlementId == null || settlementId <= 0) {
			throw new IllegalArgumentException("정산 ID는 양수여야 합니다.");
		}
		if (amount == null || amount <= 0) {
			throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
		}
		if (method == null || requestedAt == null) {
			throw new IllegalArgumentException("결제 수단과 요청 시각은 필수입니다.");
		}
		Payment payment = new Payment();
		payment.settlementId = settlementId;
		payment.amount = amount;
		payment.method = method;
		payment.status = PaymentStatus.REQUESTED;
		payment.requestedAt = requestedAt;
		return payment;
	}

	/** 같은 거래의 성공 결과를 재수신하면 최초 결제 정보를 유지한다. */
	public void markPaid(String transactionId, LocalDateTime paidAt) {
		if (transactionId == null || transactionId.isBlank() || transactionId.length() > 100) {
			throw new IllegalArgumentException("거래 ID는 1~100자의 공백이 아닌 값이어야 합니다.");
		}
		if (paidAt == null || paidAt.isBefore(requestedAt)) {
			throw new IllegalArgumentException("결제 시각은 요청 시각보다 빠를 수 없습니다.");
		}
		if (status == PaymentStatus.PAID && transactionId.equals(this.transactionId)) {
			return;
		}
		requireRequested();
		this.transactionId = transactionId;
		this.paidAt = paidAt;
		this.status = PaymentStatus.PAID;
	}

	/** PG가 명확히 거절한 경우만 호출한다. 타임아웃 등 결과 불명은 실패가 아니다. */
	public void markFailed(String failureReason) {
		if (failureReason == null || failureReason.isBlank() || failureReason.length() > 500) {
			throw new IllegalArgumentException("실패 사유는 1~500자의 공백이 아닌 값이어야 합니다.");
		}
		if (status == PaymentStatus.FAILED && failureReason.equals(this.failureReason)) {
			return;
		}
		requireRequested();
		this.failureReason = failureReason;
		this.status = PaymentStatus.FAILED;
	}

	private void requireRequested() {
		if (status != PaymentStatus.REQUESTED) {
			throw new IllegalStateException("결과가 확정된 결제는 변경할 수 없습니다: " + status);
		}
	}
}
