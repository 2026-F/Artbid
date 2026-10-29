package com.artbid.auction.live;

import java.util.Optional;

/**
 * 경매 실시간 상태 캐시.
 * 캐시는 보조 수단이다. 저장소에 문제가 있어도 예외를 던지지 않고, 조회는 빈 값을 돌려줘서 DB로 대체하게 한다.
 */
public interface AuctionLiveCache {

	Optional<AuctionLiveState> find(Long auctionId);

	/** 변경 이벤트 반영. 이미 저장된 변경보다 version이 작으면(더 오래된 변경이면) 무시한다. */
	void saveChange(AuctionLiveState state);

	/** DB에서 읽은 스냅샷 저장. 이미 값이 있으면 무시한다. 변경 이벤트가 먼저 들어온 값을 덮어쓰지 않기 위함. */
	void saveSnapshotIfAbsent(AuctionLiveState state);
}
