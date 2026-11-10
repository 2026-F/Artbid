package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.Bid;
import com.artbid.auction.live.AuctionLiveChangedEvent;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveState;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.auction.repository.BidRepository;
import com.artbid.common.exception.BidTemporarilyUnavailableException;
import com.artbid.common.exception.InvalidBidException;
import com.artbid.infra.realtime.AuctionSseRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher eventPublisher;
	private final AuctionPriceRedisService priceRedisService;

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
		broadcastAfterCommit(auctionId, result); // 기존 화면 호환용 이름 없는 이벤트. 유지한다.
		publishLiveChanged(auction, now, AuctionLiveReason.BID_CANCELED, false);
		return result;
	}

	@Transactional
	public BidResult submitBid(Long auctionId, Long bidderId, Long price) {
		// 더 이상 findByIdForUpdate(락 거는 조회)가 아니라 그냥 findById다.
		// "누가 이겼는지" 판정은 이제 아래 Redis 호출이 담당한다.
		Auction auction = auctionRepository.findById(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

		AuctionPriceRedisService.UpdateResult redisResult =
				priceRedisService.tryUpdateCurrentPrice(auctionId, price, auction.getMinBidUnit());

		if (redisResult == AuctionPriceRedisService.UpdateResult.CACHE_MISS) {
			// Redis에 캐시가 비어있던 경우: DB 값으로 채우고 한 번 더 시도
			priceRedisService.primeCurrentPrice(auctionId, auction.getCurrentPrice());
			redisResult = priceRedisService.tryUpdateCurrentPrice(auctionId, price, auction.getMinBidUnit());
		}

		if (redisResult == AuctionPriceRedisService.UpdateResult.REJECTED) {
			throw new InvalidBidException(
					"입찰가가 너무 낮습니다. 현재가 " + auction.getCurrentPrice() + "원, 최소 "
							+ (auction.getCurrentPrice() + auction.getMinBidUnit()) + "원 이상 입력하세요.");
		}

		// ------------------------------------------------------------------
		// TODO: 이 아래는 아직 예전 방식(동기 DB 반영) 그대로다.
		// 이번 단계 목표는 "Redis 기반 판정"이 동시성 문제를 실제로 줄여주는지
		// k6로 검증하는 것까지라서, DB 반영/안티 스나이핑/입찰 내역 저장은
		// 다음 단계에서 비동기로 옮긴다. 지금은 auction.applyBid()가 내부적으로
		// currentPrice를 다시 한번 검증하는 중복이 있는데, 당장은 안전망으로 남겨둔다.
		// ------------------------------------------------------------------
		LocalDateTime now = LocalDateTime.now();
		boolean extended = auction.applyBid(price, now);
		bidRepository.save(new Bid(auctionId, bidderId, price, now));

		BidResult result = new BidResult(auction.getCurrentPrice(), auction.getAuctionEndAt(), extended);
		broadcastAfterCommit(auctionId, result);
		publishLiveChanged(auction, now, AuctionLiveReason.BID, extended);
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

	// 실시간 상태 캐시 갱신과 auction-live 이벤트 전송은 AuctionLiveEventListener가 커밋 후에 처리한다.
	// now는 경매 행 락을 잡은 뒤의 시각이라 같은 경매 안에서는 커밋 순서와 같다(캐시의 오래된 변경 판별에 사용).
	private void publishLiveChanged(Auction auction, LocalDateTime now, AuctionLiveReason reason, boolean extended) {
		eventPublisher.publishEvent(new AuctionLiveChangedEvent(AuctionLiveState.changed(auction, now), reason, extended));
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