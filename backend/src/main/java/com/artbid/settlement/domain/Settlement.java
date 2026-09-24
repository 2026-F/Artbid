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
