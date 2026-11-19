package com.artbid.auction.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 입찰 내역 조회(GET /api/auctions/{id}/bids)는 "이 경매의 입찰을 최신순으로"를 찾는다.
 */
@Table(indexes = @Index(name = "idx_bid_auction_created", columnList = "auctionId, createdAt DESC"))
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

	public boolean canceled;
	public LocalDateTime cancelAt;

	public Bid(Long auctionId, Long bidderId, Long price, LocalDateTime createdAt) {
		this.auctionId = auctionId;
		this.bidderId = bidderId;
		this.price = price;
		this.createdAt = createdAt;
		this.canceled = false;
	}

	public void cancel(LocalDateTime now) {
		this.canceled = true;
		this.cancelAt = now;
	}
}
