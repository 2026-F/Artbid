package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingScheduler {

    private final AuctionRepository auctionRepository;
    private final AuctionClosingService auctionClosingService;

    @Scheduled(fixedDelay = 10_000)
    public void closeEndedAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> ended = auctionRepository.findEndedAuctions(now);

        for (Auction auction : ended) {
            try {
                // 다른 Bean을 통해 호출하므로 프록시가 적용돼서 경매 하나당 별도 트랜잭션으로 처리됨
                // → 하나가 실패해도 나머지는 그대로 커밋되고, 실패한 건만 다음 틱에 재시도됨
                auctionClosingService.closeOne(auction.getId(), now);
            } catch (Exception e) {
                log.error("[경매 마감] auctionId={} 처리 실패, 다음 스케줄에서 재시도", auction.getId(), e);
            }
        }
    }
}