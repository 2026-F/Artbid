package com.artbid.auction.repository;

import com.artbid.auction.domain.Bid;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("select b from Bid b where b.auctionId = :auctionId order by b.createdAt desc")
    List<Bid> findHistory(@Param("auctionId") Long auctionId);

    // "본인의 가장 최근 입찰" 찾기
    @Query("select b from Bid b where b.auctionId = :auctionId and b.bidderId = :bidderId and b.canceled = false order by b.createdAt desc")
    Optional<Bid> findMyLatestBid(@Param("auctionId") Long auctionId, @Param("bidderId") Long bidderId, Limit limit);

    // 취소 후 currentPrice를 되돌릴 대상 — 취소되지 않은 입찰 중 최고가
    @Query("select b from Bid b where b.auctionId = :auctionId and b.canceled = false order by b.price desc")
    Optional<Bid> findTopBid(@Param("auctionId") Long auctionId, Limit limit);
}
