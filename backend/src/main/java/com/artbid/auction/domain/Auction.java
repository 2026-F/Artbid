package com.artbid.auction.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Auction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long artworkId;
	private LocalDateTime previewStart;
	private LocalDateTime previewEnd;
	private LocalDateTime auctionEndAt;

	@Enumerated(EnumType.STRING)
	private AuctionStatus status;
}
