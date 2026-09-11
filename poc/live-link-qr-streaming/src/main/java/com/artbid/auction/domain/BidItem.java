package com.artbid.auction.domain;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 서버 메모리에 보관하는 경매 아이템 상태 (PoC 용, DB 없음).
 * 실 서비스의 auction 도메인 엔티티가 들어갈 자리를 흉내 낸 것으로,
 * 영속성/동시성 제어(Redis Lua, 안티 스나이핑 등)는 이 PoC의 검증 범위가 아니다.
 */
public class BidItem {

    @Getter
    private final String itemId;
    @Getter
    private final String title;
    @Getter
    private final String youtubeVideoId;

    private final AtomicLong currentPrice;
    private final AtomicLong bidCount = new AtomicLong(0);

    public BidItem(String itemId, String title, String youtubeVideoId, long initialPrice) {
        this.itemId = itemId;
        this.title = title;
        this.youtubeVideoId = youtubeVideoId;
        this.currentPrice = new AtomicLong(initialPrice);
    }

    public long getCurrentPrice() {
        return currentPrice.get();
    }

    public long getBidCount() {
        return bidCount.get();
    }

    /** increment 만큼 호가를 올리고 갱신된 가격을 반환한다. */
    public long addBid(long increment) {
        bidCount.incrementAndGet();
        return currentPrice.addAndGet(increment);
    }
}
