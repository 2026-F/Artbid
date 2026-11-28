package com.artbid.liveshow.repository;

import com.artbid.liveshow.domain.LiveShowLot;
import com.artbid.liveshow.domain.LotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LiveShowLotRepository extends JpaRepository<LiveShowLot, Long> {

	List<LiveShowLot> findByShowIdOrderByLotNo(Long showId);

	List<LiveShowLot> findByShowIdIn(Collection<Long> showIds);

	List<LiveShowLot> findByShowIdAndStatus(Long showId, LotStatus status);

	boolean existsByAuctionId(Long auctionId);

	boolean existsByAuctionIdIn(Collection<Long> auctionIds);
}
