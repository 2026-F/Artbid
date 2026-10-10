package com.artbid.streaming.repository;

import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.domain.LivestreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LivestreamRepository extends JpaRepository<Livestream, Long> {

	Optional<Livestream> findByAuctionId(Long auctionId);

	List<Livestream> findAllByStatus(LivestreamStatus status);
}
