package com.artbid.auction.service;


import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionClosingService {

    private static final double PREMIUM_RATE = 0.10; // 낙찰가의 10%를 수수료로
    private static final long SHIPPING_FEE = 30_000L;
    private static final long PAYMENT_DEADLINE_DAYS = 3;

    private final AuctionRepository auctionRepository;
    private final ArtworkRepository artworkRepository;
    private final BidRepository bidRepository;
    private final SettlementRepository settlementRepository;

    @Scheduled(fixedDelay = 10_000) // 10초마다 마감 지난 경매 확인
    @Transactional
    public void closeEndedAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> ended = auctionRepository.findEndedAuctions(now);
        for (Auction auction : ended) {
            closeOne(auction.getId(), now);
        }
    }

    @Transactional
    public void closeOne(Long auctionId, LocalDateTime now){
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

        if (auction.getStatus() == AuctionStatus.CLOSED) {
            return; // 이미 처리됨
        }

        auction.close(now);

        bidRepository.findTopBid(auctionId, Limit.of(1)).ifPresent(winningBid -> {
            long finalPrice = auction.getCurrentPrice();
            long premiumFee = Math.round(finalPrice * PREMIUM_RATE);

            Settlement settlement = Settlement.create(
                    auctionId, winningBid.getBidderId(), finalPrice,
                    premiumFee, SHIPPING_FEE, now.plusDays(PAYMENT_DEADLINE_DAYS));
            settlementRepository.save(settlement);

            Artwork artwork = artworkRepository.findById(auction.getArtworkId())
                    .orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다: " + auction.getArtworkId()));
            artwork.markSold();
        });
        // 입찰이 하나도 없었으면(유찰) Settlement 없이 그냥 CLOSED만 됨
    }
}
