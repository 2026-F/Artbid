package com.artbid.auction.service;


import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.infra.realtime.AuctionSseRegistry;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private final AuctionSseRegistry sseRegistry;

    @Transactional
    public void closeOne(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

        if (auction.getStatus() == AuctionStatus.CLOSED) {
            return; // 이미 처리됨
        }

        auction.close(now);

        Optional<Bid> winningBid = bidRepository.findTopBid(auctionId, Limit.of(1));
        winningBid.ifPresent(bid -> {
            long finalPrice = auction.getCurrentPrice();
            long premiumFee = Math.round(finalPrice * PREMIUM_RATE);

            Settlement settlement = Settlement.create(
                    auctionId, bid.getBidderId(), finalPrice,
                    premiumFee, SHIPPING_FEE, now.plusDays(PAYMENT_DEADLINE_DAYS));
            settlementRepository.save(settlement);

            Artwork artwork = artworkRepository.findById(auction.getArtworkId())
                    .orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다: " + auction.getArtworkId()));
            artwork.markSold();
        });

        sseRegistry.broadcastAfterCommit(auctionId,
                new AuctionClosedEvent(auction.getCurrentPrice(), winningBid.isPresent()));
        // → 경매 DB 상태(CLOSED) + Settlement 생성까지 전부 커밋 성공하면
        //   그 순간 이 경매를 보고 있던 모든 사람한테 "마감됐어요, 최종가는 얼마예요" 알림이 동시에 나감
    }

    public record AuctionClosedEvent(Long finalPrice, boolean sold) {
    }
}