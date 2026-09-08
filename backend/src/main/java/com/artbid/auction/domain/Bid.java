package com.artbid.auction.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Bid {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long auctionId;
	private Long bidderId;
	private Long price;
	private LocalDateTime createdAt;

	public Bid(Long auctionId, Long bidderId, Long price, LocalDateTime createdAt) {
		this.auctionId = auctionId;
		this.bidderId = bidderId;
		this.price = price;
		this.createdAt = createdAt;
	}
}
