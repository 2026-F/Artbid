package com.artbid.auction.service;

import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
	private final PlatformTransactionManager transactionManager;

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

	// 이 메서드 전체를 @Transactional로 감싸지 않는다. 그렇게 하면 findById가 커넥션을
	// 받아오자마자, 뒤이어 나오는 Redis 네트워크 호출이 끝날 때까지 그 커넥션을 계속 쥐고
	// 있게 된다. Redis는 싱글 스레드라 경합이 심해지면 그 호출 자체가 오래 걸릴 수 있는데,
	// 그동안 DB 커넥션을 아무 일도 안 하면서 쥐고만 있으면 "커넥션 풀 고갈"이라는 원래
	// 문제가 (무엇을 기다리는지만 바뀐 채) 그대로 재현된다. 그래서 DB 조회는 트랜잭션
	// 밖에서 하고(JPA가 그 호출 하나만을 위한 아주 짧은 트랜잭션을 열었다 바로 커넥션을
	// 반납해준다), 실제 DB 쓰기만 아래 TransactionTemplate으로 필요한 순간에만 짧게
	// 트랜잭션을 연다.
	public BidResult submitBid(Long auctionId, Long bidderId, Long price) {
		// 더 이상 findByIdForUpdate(락 거는 조회)가 아니라 그냥 findById다.
		// "누가 이겼는지" 판정은 이제 아래 Redis 호출이 담당한다.
		Auction auction = auctionRepository.findById(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));

		// 가격 비교(Redis)보다 먼저 "애초에 입찰 가능한 경매인지"부터 거른다.
		// 순서를 바꾸지 않으면, Redis는 "이겼다"고 판정했는데 사실 마감/상태 문제로
		// DB 쪽에서 거부되는 경우가 생기고, 그 어긋남은 아무도 되돌리지 않는다.
		LocalDateTime now = LocalDateTime.now();
		auction.assertBiddable(now);

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

		// expectedStatus는 "이 요청이 읽었던 시점의 상태"를 그대로 보존해둔 것 — 아래
		// UPDATE가 그 사이 관리자가 상태를 바꿔놓은 경우를 잡아낼 수 있게 해준다.
		// auction은 트랜잭션 밖에서 읽혀서 이미 detach된 상태라, applyBid는 메모리
		// 계산만 하고 DB에는 아무 영향도 안 준다.
		AuctionStatus expectedStatus = auction.getStatus();
		boolean extended = auction.applyBid(price, now);

		TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
		try {
			return writeTransaction.execute(status -> {
				int updatedRows = auctionRepository.updateIfHigherPrice(
						auctionId, auction.getCurrentPrice(), auction.getAuctionEndAt(), auction.getStatus(), expectedStatus);

				if (updatedRows == 0) {
					// Redis에서는 이겼지만, DB에 반영하는 그 짧은 틈에 다른 요청이 먼저
					// (같거나 더 높은 가격으로) 반영했거나, 관리자가 경매 상태를 바꿔놓은 경우다.
					// 두 경우 다 Redis 값을 되돌릴 필요는 없다 — 전자는 더 높은 가격이 이미
					// Redis를 거쳐왔으니 여전히 맞고, 후자는 가격 자체는 안 바뀌었으니 역시 맞다.
					// 이 요청만 실패 처리하면 된다.
					throw new BidTemporarilyUnavailableException();
				}

				bidRepository.save(new Bid(auctionId, bidderId, price, now));

				BidResult result = new BidResult(auction.getCurrentPrice(), auction.getAuctionEndAt(), extended);
				broadcastAfterCommit(auctionId, result);
				publishLiveChanged(auction, now, AuctionLiveReason.BID, extended);
				return result;
			});

		} catch (BidTemporarilyUnavailableException e) {
			throw e; // 위 설명대로 Redis는 이미 맞는 상태라 보정 불필요
		} catch (RuntimeException e) {
			// 예상 못한 실패(DB 에러 등) — Redis만 "이겼다"고 알고 있고 DB엔 반영 안 된 상태로
			// 남을 수 있다. 이 트랜잭션은 이미 실패한(Postgres 기준 aborted) 상태일 수 있어서,
			// 같은 트랜잭션 안에서 DB를 다시 읽어 되돌리려 하면 그 조회마저 실패해 원래 예외를
			// 가려버릴 수 있다. 그래서 DB를 다시 읽지 않고 Redis 캐시를 비운다 — 다음 요청이
			// CACHE_MISS로 떨어지면서 새 트랜잭션에서 DB 값으로 다시 채워진다. 이 보정 자체가
			// 실패해도 원래 예외는 그대로 전파되게 따로 감싼다.
			try {
				priceRedisService.invalidateCurrentPrice(auctionId);
			} catch (RuntimeException redisEx) {
				// 보정 실패는 로그로만 남기고 원래 예외를 덮지 않는다.
			}
			throw e;
		}
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