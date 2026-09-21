package com.artbid.auction.repository;

import com.artbid.auction.domain.Auction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;


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
}

