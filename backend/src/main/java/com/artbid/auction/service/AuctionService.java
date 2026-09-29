package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.artbid.artwork.repository.ArtworkRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionService {

	private final AuctionRepository auctionRepository;

	public Auction getAuction(Long id) {
		return auctionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + id));
	}

	public Auction createAuction(Long artworkId,
								 Long startPrice,
								 Long minBidUnit,
								 LocalDateTime previewStart,
								 LocalDateTime previewEnd,
								 LocalDateTime auctionEndAt) {
		auctionRepository.findById(artworkId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다 :" + artworkId));

		Auction auction = Auction.create(artworkId, startPrice, minBidUnit, previewStart, previewEnd, auctionEndAt, LocalDateTime.now());


		return auctionRepository.save(auction);
	}



}
