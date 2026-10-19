package com.artbid.auction.dto;

import com.artbid.auction.domain.*;

import java.time.LocalDateTime;

public record AuctionDetailResponse(
        Long id,
        Long currentPrice,
        Long startPrice,
        Long minBidUnit,
        LocalDateTime previewStart,
        LocalDateTime previewEnd,
        LocalDateTime auctionEndAt,
        AuctionStatus status,
        String title,
        String imageUrl) {

}
