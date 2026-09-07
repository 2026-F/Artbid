package com.artbid.auction.repository;

import com.artbid.auction.domain.Auction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Optional;

import static org.hibernate.LockMode.PESSIMISTIC_WRITE;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    // PESSIMISTIC_WRITE = SELECT ... FOR UPDATE
    // 트랜잭션이 끝날 때까지 이 경매 행에 대한 다른 트랜잭션의 락 획득을 막는다.
    // 그래서 동시에 여러 입찰이 들어와도 한 번에 하나씩만 이 메서드를 통과해서 처리된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);
}

