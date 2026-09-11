package com.artbid.auction.domain;

import lombok.Getter;

import java.time.Instant;

/**
 * WebSocket(/topic/bid/{itemId})으로 브로드캐스트되는 실시간 호가 갱신 메시지.
 */
@Getter
public class BidUpdateMessage {

    private final String itemId;
    private final long currentPrice;
    private final long bidCount;
    private final String source; // "AUTO_SIM" | "MANUAL"
    private final long serverTimeEpochMillis;

    public BidUpdateMessage(String itemId, long currentPrice, long bidCount, String source) {
        this.itemId = itemId;
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
        this.source = source;
        this.serverTimeEpochMillis = Instant.now().toEpochMilli();
    }
}
