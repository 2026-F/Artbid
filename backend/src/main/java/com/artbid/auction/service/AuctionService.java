package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionService {

	private final AuctionRepository auctionRepository;

	public Auction getAuction(Long id) {
		return auctionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + id));
	}
}
