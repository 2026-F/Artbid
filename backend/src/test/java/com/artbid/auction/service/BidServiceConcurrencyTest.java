package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.common.exception.InvalidBidException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

// 로컬 docker-compose의 postgres·redis가 떠 있어야 통과함 (application.yml이 localhost를 보고 있음)
@SpringBootTest
class BidServiceConcurrencyTest {

    @Autowired
    private BidService bidService;
    @Autowired
    private AuctionRepository auctionRepository;

    @Test
    void 동시_입찰_100건_중_낮은_가격이_현재가로_반영되면_안된다() throws InterruptedException {
        // given: 시작가 10,000원, 최소 입찰 단위 1,000원, 10분 뒤 마감인 경매 하나 생성
        Auction auction = Auction.builder()
                .artworkId(1L)
                .startPrice(10_000L)
                .currentPrice(10_000L)
                .minBidUnit(1_000L)
                .previewStart(LocalDateTime.now().minusDays(1))
                .previewEnd(LocalDateTime.now())
                .auctionEndAt(LocalDateTime.now().plusMinutes(10))
                .status(AuctionStatus.ONGOING)
                .build();
        auction = auctionRepository.save(auction);
        Long auctionId = auction.getId();

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Long> acceptedPrices = Collections.synchronizedList(new ArrayList<>());

        // when: 서로 다른 100개 가격(11000원 ~ 110000원)으로 동시에 입찰
        for (int i = 1; i <= threadCount; i++) {
            long price = 10_000L + i * 1_000L;
            long bidderId = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 100개 스레드가 동시에 출발하도록 대기
                    BidService.BidResult result = bidService.submitBid(auctionId, bidderId, price);
                    acceptedPrices.add(result.currentPrice());
                } catch (InvalidBidException e) {
                    // 이미 더 높은 가격이 먼저 반영된 경우 -> 정상적으로 거절된 것 (버그 아님)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // 신호 -> 100개 스레드 거의 동시에 입찰 시작
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(finished).as("타임아웃 없이 전부 처리됐는지").isTrue();

        Long finalPriceInDb = auctionRepository.findById(auctionId).orElseThrow().getCurrentPrice();
        Long maxAcceptedPrice = acceptedPrices.stream().max(Long::compareTo).orElseThrow();

        // 핵심 검증: race condition이 있었다면 낮은 가격이 나중에 커밋되면서
        // DB의 최종 현재가가 실제로 받아들여졌던 최고가보다 낮아지는 현상이 생긴다.
        // 이게 같다는 건 "낮은 가격이 통과한 사례 0건"이라는 뜻.
        assertThat(finalPriceInDb).isEqualTo(maxAcceptedPrice);
    }
}