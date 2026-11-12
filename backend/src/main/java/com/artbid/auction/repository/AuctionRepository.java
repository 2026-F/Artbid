package com.artbid.auction.repository;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;


import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    // PESSIMISTIC_WRITE = SELECT ... FOR UPDATE
    // 트랜잭션이 끝날 때까지 이 경매 행에 대한 다른 트랜잭션의 락 잠금.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint( name = "jakarta.persistence.lock.timeout", value = "3000")})
    //Lock이랑 같이 쓸 때 JPQL
    @Query("select a from Auction a where a.id = :id")

    // 위 쿼리로 id 보내는 용도
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);

    List<Auction> findByArtworkIdIn(Collection<Long> artworkIds);

    @Query("select a from Auction a where a.status in ('ONGOING','EXTENDED') and a.auctionEndAt <= :now")
    List<Auction> findEndedAuctions(@Param("now") LocalDateTime now);

    // Redis가 이미 "이 가격이 이겼다"고 판정한 뒤, 그 결과를 DB에 반영할 때 쓴다.
    // currentPrice < :currentPrice 조건 덕분에, 다른 요청의 쓰기가 먼저(같거나 더 높은 값으로)
    // 반영돼 있으면 이 UPDATE는 행을 하나도 바꾸지 않고 0을 돌려준다.
    // (그래서 나중에 도착한, 사실은 더 낮은 쓰기가 값을 거꾸로 덮어쓰는 사고를 막아준다.)
    // status in (ONGOING, EXTENDED) 조건은 그 사이에 관리자가 상태를 바꿨거나 마감된 경우를 잡아낸다.
    // 예전에는 "읽었을 때의 상태와 같은지(status = :expectedStatus)"를 봤는데, 이제 입찰은 DB를 읽지 않고
    // Redis 판정만 믿으므로 "아직 입찰 가능한 상태인지"를 본다. 마감 직전 연장으로 ONGOING -> EXTENDED가
    // 동시에 여러 건 일어나도 커밋 순서와 상관없이 정상 반영된다.
    @Modifying
    @Query("update Auction a set a.currentPrice = :currentPrice, a.auctionEndAt = :auctionEndAt, a.status = :status " +
            "where a.id = :auctionId and a.currentPrice < :currentPrice " +
            "and a.status in ('ONGOING','EXTENDED')")
    int updateIfHigherPrice(@Param("auctionId") Long auctionId,
                             @Param("currentPrice") Long currentPrice,
                             @Param("auctionEndAt") LocalDateTime auctionEndAt,
                             @Param("status") AuctionStatus status);
}

