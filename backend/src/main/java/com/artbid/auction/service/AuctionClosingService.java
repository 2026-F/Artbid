package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.event.AuctionClosedMessage;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.infra.outbox.OutboxEvent;
import com.artbid.infra.outbox.OutboxEventRepository;
import com.artbid.infra.realtime.AuctionSseRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 경매 하나를 마감한다.
 *
 * 이 트랜잭션에서 하는 일은 두 가지뿐이다.
 *   1) 경매를 CLOSED로 바꾼다
 *   2) 낙찰자가 있으면 "경매 마감" 이벤트를 outbox_event에 기록한다
 * 정산 생성과 작품 판매완료 처리는 이벤트를 받은 소비자(AuctionClosedStreamConsumer)가 한다.
 *
 * 1)과 2)는 같은 트랜잭션이라 같이 커밋되거나 같이 롤백된다(Outbox 패턴).
 * 그래서 "경매는 마감됐는데 정산 이벤트가 없다"는 상태가 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AuctionClosingService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionSseRegistry sseRegistry;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void closeOne(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

        if (auction.getStatus() == AuctionStatus.CLOSED) {
            return; // 이미 처리됨
        }

        auction.close(now);

        Optional<Bid> winningBid = bidRepository.findTopBid(auctionId, Limit.of(1));
        winningBid.ifPresent(bid -> recordClosedEvent(auction, bid, now));

        sseRegistry.broadcastAfterCommit(auctionId,
                new AuctionClosedEvent(auction.getCurrentPrice(), winningBid.isPresent()));
        // → 경매 CLOSED + outbox 기록이 커밋되면, 이 경매를 보던 화면에 "마감됐어요, 최종가는 얼마예요"를 보낸다
    }

    // 정산을 만들 때 필요한 값을 전부 이벤트에 담는다. 소비자는 이 값만 보고 정산을 만든다.
    private void recordClosedEvent(Auction auction, Bid bid, LocalDateTime closedAt) {
        AuctionClosedMessage message = new AuctionClosedMessage(
                auction.getId(), bid.getBidderId(), auction.getArtworkId(), auction.getCurrentPrice(), closedAt);

        outboxEventRepository.save(
                OutboxEvent.create(AuctionClosedMessage.TOPIC, String.valueOf(auction.getId()), toJson(message)));
    }

    // 예전에는 여기서 실패하면 로그만 남기고 넘어갔다. 정산이 이 트랜잭션 안에서 만들어졌기 때문이다.
    // 이제 정산은 이 이벤트로만 만들어지므로, 이벤트를 못 남기면 마감 자체를 롤백한다.
    // 롤백되면 경매는 마감 전 상태로 남고, 다음 스케줄러 주기(10초)에 다시 마감을 시도한다.
    private String toJson(AuctionClosedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("경매 마감 이벤트를 만들 수 없습니다: " + message.auctionId(), e);
        }
    }

    public record AuctionClosedEvent(Long finalPrice, boolean sold) {
    }
}