package com.artbid.settlement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long auctionId;
	private Long winnerId;
	private Long finalPrice;
	private Long premiumFee;
	private Long shippingFee;
	private Long totalAmount;
	private LocalDateTime paymentDeadlineAt;

	@Enumerated(EnumType.STRING)
	private PaymentStatus paymentStatus;

	// 결제 기한이 지나 정산 배치가 FAILED로 바꾼 시각. 결제 기한 만료가 아닌 경우는 null.
	private LocalDateTime expiredAt;

	public static Settlement create(Long auctionId, Long winnerId, long finalPrice,
			long premiumFee, long shippingFee, LocalDateTime paymentDeadlineAt) {
		if (auctionId == null || auctionId <= 0 || winnerId == null || winnerId <= 0
				|| finalPrice <= 0 || premiumFee < 0 || shippingFee < 0 || paymentDeadlineAt == null) {
			throw new IllegalArgumentException("정산 ID, 금액 및 결제기한을 확인해주세요.");
		}
		Settlement settlement = new Settlement();
		settlement.auctionId = auctionId;
		settlement.winnerId = winnerId;
		settlement.finalPrice = finalPrice;
		settlement.premiumFee = premiumFee;
		settlement.shippingFee = shippingFee;
		settlement.totalAmount = Math.addExact(Math.addExact(finalPrice, premiumFee), shippingFee);
		settlement.paymentDeadlineAt = paymentDeadlineAt;
		settlement.paymentStatus = PaymentStatus.PENDING;
		return settlement;
	}

	public boolean isPayableAt(LocalDateTime now) {
		return paymentStatus == PaymentStatus.PENDING && totalAmount != null && totalAmount > 0
				&& paymentDeadlineAt != null && now.isBefore(paymentDeadlineAt);
	}

	/**
	 * 결제 기한이 지난 미결제 정산을 FAILED로 바꾼다. 바꿨으면 true.
	 * 새 상태(EXPIRED)를 추가하지 않고 기존 FAILED를 쓴다. 기존 DB의 payment_status 컬럼에는 Hibernate가 만든
	 * CHECK 제약(PENDING, PAID, FAILED)이 있어 새 값을 저장할 수 없기 때문이다. 만료 여부는 expiredAt으로 구분한다.
	 * 진행 중인 결제(결과 불명)가 있는지는 호출하는 쪽에서 확인한다.
	 */
	public boolean expire(LocalDateTime now) {
		if (paymentStatus != PaymentStatus.PENDING || paymentDeadlineAt == null || now.isBefore(paymentDeadlineAt)) {
			return false;
		}
		paymentStatus = PaymentStatus.FAILED;
		expiredAt = now;
		return true;
	}

	public void markPaid() {
		if (paymentStatus == PaymentStatus.PAID) {
			return;
		}
		if (paymentStatus != PaymentStatus.PENDING) {
			throw new IllegalStateException("결제 대기 중인 정산만 완료할 수 있습니다.");
		}
		paymentStatus = PaymentStatus.PAID;
	}
}
