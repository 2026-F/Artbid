package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.common.exception.BidTemporarilyUnavailableException;
import com.artbid.common.exception.InvalidBidException;
import com.artbid.infra.realtime.AuctionSseRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidService {

	@PersistenceContext
	private EntityManager entityManager;

	private final AuctionRepository auctionRepository;
	private final BidRepository bidRepository;
	private final AuctionSseRegistry sseRegistry;

	@Transactional
	public BidResult submitBid(Long auctionId, Long bidderId, Long price) {
		Auction auction = lockAuctionOrThrow(auctionId);

		LocalDateTime now = LocalDateTime.now();
		boolean extended = auction.applyBid(price, now); // 검증 실패 시 InvalidBidException

		bidRepository.save(new Bid(auctionId, bidderId, price, now));

		BidResult result = new BidResult(auction.getCurrentPrice(), auction.getAuctionEndAt(), extended);
		broadcastAfterCommit(auctionId, result);
		return result;
	}

	//입찰 취소
	@Transactional
	public BidResult cancelBid(Long auctionId, Long bidderId) {
		Auction auction = lockAuctionOrThrow(auctionId);

		Bid latestBid = bidRepository
				.findMyLatestBid(auctionId, bidderId, Limit.of(1))
				.orElseThrow(() -> new IllegalArgumentException("취소할 입찰이 없습니다."));

		if (!latestBid.getPrice().equals(auction.getCurrentPrice())) {
			throw new InvalidBidException("이미 다른 입찰에 덮어써진 입찰은 취소할 수 없습니다.");
		}

		LocalDateTime now = LocalDateTime.now();
		latestBid.cancel(now);

		// currentPrice를 취소되지 않은 입찰 중 최고가로 되돌림. 남은 입찰이 없으면 시작가로.
		Long revertedPrice = bidRepository.findTopBid(auctionId, Limit.of(1))
				.map(Bid::getPrice)
				.orElse(auction.getStartPrice());
		auction.aftercancelPrice(revertedPrice);

		BidResult result = new BidResult(auction.getCurrentPrice(), auction.getAuctionEndAt(), false);
		broadcastAfterCommit(auctionId, result);
		return result;
	}

	//경매 입찰 내역 조회
	public List<BidResponse> getBids(Long auctionId) {
		return bidRepository.findHistory(auctionId).stream()
				.map(BidResponse::from)
				.toList();
	}

	// submitBid/cancelBid 둘 다 "Auction 행 락 걸고 없으면 예외, 락 대기 타임아웃되면 다른 예외" 패턴이 똑같아서 추출
	private Auction lockAuctionOrThrow(Long auctionId) {
		entityManager.createNativeQuery("SET LOCAL lock_timeout = '3000ms'").executeUpdate();
		try {
			return auctionRepository.findByIdForUpdate(auctionId)
					.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));
		} catch (PessimisticLockingFailureException e) {
			throw new BidTemporarilyUnavailableException();
		}
	}

	// 트랜잭션이 실제로 커밋된 뒤에만 구독자에게 알림 (롤백되면 알림도 안 나가야 하므로)
	private void broadcastAfterCommit(Long auctionId, BidResult result) {
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
	}

	public record BidResult(Long currentPrice, LocalDateTime auctionEndAt, boolean extended) {
	}

	public record BidResponse(Long bidId, Long bidderId, Long price, LocalDateTime createdAt,
							  boolean canceled, LocalDateTime canceledAt) {
		public static BidResponse from(Bid bid) {
			return new BidResponse(bid.getId(), bid.getBidderId(), bid.getPrice(),
					bid.getCreatedAt(), bid.isCanceled(), bid.getCancelAt());
		}
	}
}