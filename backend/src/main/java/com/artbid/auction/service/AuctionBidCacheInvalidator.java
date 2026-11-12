package com.artbid.auction.service;

import com.artbid.auction.live.AuctionLiveChangedEvent;
import com.artbid.auction.live.AuctionLiveReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 입찰 판정 캐시(auction:{id}:bid_state)를 입찰 "밖"의 변경에 맞춰 비운다.
 *
 * 이제 입찰은 상태·마감까지 Redis만 보고 판정하므로, 관리자가 상태를 바꾸거나(SNAPSHOT)
 * 경매가 마감되면(CLOSED) 캐시도 따라 바뀌어야 한다. 안 그러면 닫힌 경매에 입찰이 이기거나,
 * 다시 연 경매가 계속 "진행 중이 아님"으로 거절된다.
 *
 * 이 변경들은 이미 AuctionLiveChangedEvent로 발행되고 있어서, 여기서 받아 캐시를 지운다.
 * AuctionService / AuctionClosingService는 고칠 필요가 없다.
 *
 * - BID: 입찰 자체가 Redis를 먼저 갱신하고 오므로 지우면 안 된다 (지우면 매 입찰마다 DB 재조회).
 * - BID_CANCELED: BidService.cancelBid가 커밋 직후 직접 지운다.
 * - 커밋된 뒤에만 지운다. 커밋 전에 지우면, 그 틈에 들어온 입찰이 아직 안 바뀐 DB 값으로 다시 채울 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionBidCacheInvalidator {

	private final AuctionPriceRedisService priceRedisService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onChanged(AuctionLiveChangedEvent event) {
		if (event.reason() == AuctionLiveReason.BID || event.reason() == AuctionLiveReason.BID_CANCELED) {
			return;
		}
		Long auctionId = event.state().auctionId();
		try {
			priceRedisService.invalidate(auctionId);
		} catch (RuntimeException e) {
			// 상태 변경은 이미 커밋됐으니 실패시키지 않는다. 키는 TTL이 지나면 저절로 사라진다.
			log.warn("[입찰 캐시] auctionId={} reason={} 캐시 삭제 실패, 최대 {}초 동안 DB와 다를 수 있음: {}",
					auctionId, event.reason(), AuctionPriceRedisService.TTL_SECONDS, e.getMessage());
		}
	}
}
