package com.artbid.settlement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 위탁자에게 줄 판매 대금. 낙찰자가 결제를 마친 정산(Settlement) 하나당 하나만 만든다.
 * 지급액 = 낙찰가 - 판매 수수료(낙찰가 x 수수료율, 원 단위 반올림).
 * 낙찰자가 낸 구매 수수료와 배송비는 위탁자 몫이 아니므로 계산에 넣지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_seller_payout_settlement", columnNames = "settlementId"))
public class SellerPayout {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long settlementId;
	private Long auctionId;
	private Long artworkId;
	private Long consignorId;
	private Long finalPrice;

	@Column(precision = 5, scale = 4)
	private BigDecimal commissionRate; // 지급액을 계산할 때 적용한 수수료율 (나중에 설정이 바뀌어도 기록 유지)

	private Long commissionAmount;
	private Long payoutAmount;

	@Enumerated(EnumType.STRING)
	private SellerPayoutStatus status;

	private LocalDateTime createdAt;
	private LocalDateTime paidAt;

	public static SellerPayout ready(Settlement settlement, Long artworkId, Long consignorId,
			BigDecimal commissionRate, LocalDateTime now) {
		if (settlement == null || settlement.getPaymentStatus() != PaymentStatus.PAID) {
			throw new IllegalStateException("결제 완료된 정산만 지급 대상이 됩니다.");
		}
		if (artworkId == null || consignorId == null || now == null) {
			throw new IllegalArgumentException("작품, 위탁자, 생성 시각은 필수입니다.");
		}
		if (commissionRate == null || commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("판매 수수료율은 0 이상 1 이하여야 합니다: " + commissionRate);
		}
		long commission = BigDecimal.valueOf(settlement.getFinalPrice())
				.multiply(commissionRate)
				.setScale(0, RoundingMode.HALF_UP)
				.longValueExact();

		SellerPayout payout = new SellerPayout();
		payout.settlementId = settlement.getId();
		payout.auctionId = settlement.getAuctionId();
		payout.artworkId = artworkId;
		payout.consignorId = consignorId;
		payout.finalPrice = settlement.getFinalPrice();
		payout.commissionRate = commissionRate;
		payout.commissionAmount = commission;
		payout.payoutAmount = settlement.getFinalPrice() - commission;
		payout.status = SellerPayoutStatus.READY;
		payout.createdAt = now;
		return payout;
	}
}
