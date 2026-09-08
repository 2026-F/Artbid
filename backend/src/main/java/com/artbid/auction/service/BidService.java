package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BidService {

	private final AuctionRepository auctionRepository;
	private final BidRepository bidRepository;
	// TODO(확장 단계): 여기에 RedisTemplate 주입해서 트랜잭션 커밋 후 캐시 갱신 + Pub/Sub 발행 추가

	/**
	 * 입찰 제출 (PoC 1-1: 동시 입찰 정확성 검증 대상 로직)
	 *
	 * 트랜잭션 안에서 Auction 행을 SELECT ... FOR UPDATE로 잠그고
	 * "현재가 조회 → 검증 → 갱신"을 원자적으로 처리한다.
	 * 동시에 여러 입찰이 들어와도 같은 auctionId에 대해서는 한 번에 하나씩만
	 * 이 블록을 통과하므로(뒤에 온 요청은 락이 풀릴 때까지 대기),
	 * 낮은 가격이 먼저 커밋된 높은 가격을 덮어쓰는 race condition이 구조적으로 발생하지 않는다.
	 */
	@Transactional
	public BidResult submitBid(Long auctionId, Long bidderId, Long price) {
		Auction auction = auctionRepository.findByIdForUpdate(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

		LocalDateTime now = LocalDateTime.now();
		boolean extended = auction.applyBid(price, now); // 검증 실패 시 InvalidBidException

		bidRepository.save(new Bid(auctionId, bidderId, price, now));

		return new BidResult(auction.getCurrentPrice(), auction.getAuctionEndAt(), extended);
	}

	public record BidResult(Long currentPrice, LocalDateTime auctionEndAt, boolean extended) {
	}
}