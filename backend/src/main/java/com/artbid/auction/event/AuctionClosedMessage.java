package com.artbid.auction.event;

import java.time.LocalDateTime;

/**
 * 경매가 낙찰로 마감됐다는 이벤트. outbox에 JSON으로 저장되고, 발행기가 Redis Stream(stream:auction.closed)으로 보낸다.
 * 소비자(AuctionClosedStreamConsumer)가 이걸 받아 정산을 만든다.
 *
 * closedAt: 경매가 마감된 시각. 결제 기한(마감 + 3일)을 이 값 기준으로 잡는다.
 *           소비자가 처리한 시각을 쓰면 재처리될 때마다 기한이 밀리기 때문이다.
 */
public record AuctionClosedMessage(
        Long auctionId,
        Long bidderId,     // 낙찰자 (최고가 입찰자)
        Long artworkId,
        Long finalPrice,
        LocalDateTime closedAt) {

    public static final String TOPIC = "auction.closed";
}