package com.artbid.settlement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Settlement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long auctionId;
	private Long winnerId;
	private Long finalPrice;
	private Long premiumFee;

	@Enumerated(EnumType.STRING)
	private PaymentStatus paymentStatus;
}
