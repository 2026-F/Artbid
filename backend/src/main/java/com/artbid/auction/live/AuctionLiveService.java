package com.artbid.auction.live;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 경매 실시간 상태 조회.
 * 캐시를 먼저 보고, 없으면(첫 조회, 만료, Redis 장애) DB에서 읽어 캐시를 채운다.
 */
@Service
@RequiredArgsConstructor
public class AuctionLiveService {

	private final AuctionLiveCache cache;
	private final AuctionRepository auctionRepository;
	private final ApplicationEventPublisher eventPublisher;

	public AuctionLiveView getView(Long auctionId, AuctionLiveReason reason) {
		AuctionLiveState state = cache.find(auctionId).orElseGet(() -> loadAndCache(auctionId));
		return state.toView(LocalDateTime.now(), reason, false);
	}

	/**
	 * DB의 최신 값으로 변경 이벤트를 발행한다. 마감처럼 다른 서비스가 커밋을 끝낸 뒤 상태를 알릴 때 쓴다.
	 * 커밋이 끝난 뒤에 읽으므로 변경 시각(지금)이 앞선 입찰들의 시각보다 늦다.
	 */
	public void publishCurrent(Long auctionId, AuctionLiveReason reason) {
		Auction auction = findAuction(auctionId);
		eventPublisher.publishEvent(new AuctionLiveChangedEvent(
				AuctionLiveState.changed(auction, LocalDateTime.now()), reason, false));
	}

	private AuctionLiveState loadAndCache(Long auctionId) {
		AuctionLiveState state = AuctionLiveState.snapshot(findAuction(auctionId));
		cache.saveSnapshotIfAbsent(state);
		return state;
	}

	private Auction findAuction(Long auctionId) {
		return auctionRepository.findById(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));
	}
}
