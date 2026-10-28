package com.artbid.infra.kafka;

public record AuctionClosedMessage(Long auctionId, Long bidderId, Long artworkId, Long finalPrice) {

    public static final String TOPIC = "auction.closed";
}