package com.artbid.streaming.repository;

import com.artbid.streaming.domain.Livestream;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LivestreamRepository extends JpaRepository<Livestream, Long> {

	Optional<Livestream> findByAuctionId(Long auctionId);
}
