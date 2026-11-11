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
    // status = :expectedStatus 조건은 그 사이에 관리자가 상태를 바꿔놓은 경우를 잡아낸다 —
    // 상태를 읽은 시점과 이 UPDATE 사이에 상태가 바뀌었으면 0건으로 실패시켜서, 입찰이
    // 관리자의 상태 변경을 조용히 덮어쓰는 걸 막는다. id로 이미 찾아간 같은 행이라 status
    // 하나 더 비교하는 건 추가 조회나 인덱스 스캔이 아니라 그 행 안에서 값 비교 하나 더
    // 하는 것뿐이라 눈에 보일 지연은 없다.
    @Modifying
    @Query("update Auction a set a.currentPrice = :currentPrice, a.auctionEndAt = :auctionEndAt, a.status = :status " +
            "where a.id = :auctionId and a.currentPrice < :currentPrice and a.status = :expectedStatus")
    int updateIfHigherPrice(@Param("auctionId") Long auctionId,
                             @Param("currentPrice") Long currentPrice,
                             @Param("auctionEndAt") LocalDateTime auctionEndAt,
                             @Param("status") AuctionStatus status,
                             @Param("expectedStatus") AuctionStatus expectedStatus);
}

