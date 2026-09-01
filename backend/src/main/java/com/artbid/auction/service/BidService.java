package com.artbid.auction.service;

import com.artbid.infra.redis.BidLuaExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BidService {

	private final BidLuaExecutor bidLuaExecutor;
	// TODO: Kafka 연결 후 BidEventProducer 주입해서 유효 입찰만 이벤트 발행

	/**
	 * 입찰 제출.
	 * 1. Redis Lua compare-and-set으로 현재가 대비 유효성 검증 + 안티 스나이핑 연장 판정 (TODO)
	 * 2. 유효한 입찰만 Kafka(bid-events, 파티션 키=auctionId)로 발행 (TODO)
	 * 3. SSE로 구독 중인 클라이언트에 새 현재가 브로드캐스트 (TODO)
	 */
	public void submitBid(Long auctionId, Long bidderId, Long price) {
		bidLuaExecutor.tryBid(auctionId, bidderId, price);
	}
}
