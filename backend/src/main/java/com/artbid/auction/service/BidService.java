package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.infra.realtime.AuctionSseRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.artbid.common.exception.BidTemporarilyUnavailableException;
import org.springframework.dao.PessimisticLockingFailureException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BidService {

	@PersistenceContext
	private EntityManager entityManager;

	private final AuctionRepository auctionRepository;
	private final BidRepository bidRepository;
	private final AuctionSseRegistry sseRegistry;
	// TODO(확장 단계): 인스턴스가 여러 대로 늘어나면 AuctionSseRegistry(인메모리)만으로는 부족 —
	// Redis Pub/Sub으로 교체해서 인스턴스 간 이벤트를 중계해야 함

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
		Auction auction;
		entityManager.createNativeQuery("SET LOCAL lock_timeout = '3000ms'").executeUpdate();

		try{
			auction = auctionRepository.findByIdForUpdate(auctionId)
					.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));
		}
		catch(PessimisticLockingFailureException e){
			throw new BidTemporarilyUnavailableException();
		}

		LocalDateTime now = LocalDateTime.now();
		boolean extended = auction.applyBid(price, now); // 검증 실패 시 InvalidBidException

		bidRepository.save(new Bid(auctionId, bidderId, price, now));

		BidResult result = new BidResult(auction.getCurrentPrice(), auction.getAuctionEndAt(), extended);

		// 트랜잭션이 실제로 커밋된 뒤에만 구독자에게 알림 (롤백되면 알림도 안 나가야 하므로)
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					sseRegistry.broadcast(auctionId, result);
				}
			});
		} else {
			sseRegistry.broadcast(auctionId, result);
		}

		return result;
	}

	public record BidResult(Long currentPrice, LocalDateTime auctionEndAt, boolean extended) {
	}
}
