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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

	@PersistenceContext
	private EntityManager entityManager;

	// 같은 경매 행을 다른 트랜잭션(마감, 입찰 취소, 재오픈 등)이 잡고 있을 때 최대 3초까지만 기다린다.
	// 넘기면 Postgres가 lock_timeout 에러를 내고, 우리는 503(BidTemporarilyUnavailableException)으로 돌려준다.
	// SET LOCAL이라 이 트랜잭션 안에서만 적용되고, 커밋/롤백되면 원래 설정으로 돌아간다.
	private static final String LOCK_TIMEOUT_SQL = "SET LOCAL lock_timeout = '3000ms'";

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

		// DB 현재가를 되돌렸으니 Redis 현재가 캐시도 맞춰야 한다. 안 그러면 Redis에는 취소된
		// 입찰가가 그대로 남아서, DB 기준으로는 이기는 입찰이 Redis에서 "너무 낮다"며 거절된다.
		invalidatePriceCacheAfterCommit(auctionId);

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
		AuctionStatus expectedStatus = auction.getStatus();

		// applyBid로 엔티티를 바꾸기 전에 영속성 컨텍스트에서 떼어낸다.
		// OSIV(spring.jpa.open-in-view)가 꺼져 있으면 auction은 이미 detach된 상태라 이 호출은 아무 일도 안 한다.
		// 하지만 OSIV가 켜져 있으면 요청 전체가 영속성 컨텍스트 하나를 공유해서 auction이 아직 관리 대상이고,
		// 그러면 아래 쓰기 트랜잭션이 커밋될 때 변경 감지(dirty checking)가 applyBid로 바뀐 값을
		// 조건 없는 UPDATE로 그대로 써버린다. 그 순간 "더 낮은 가격이 늦게 와도 못 덮어쓴다"는
		// 조건부 UPDATE의 보호가 무력해진다. 설정과 상관없이 안전하도록 명시적으로 떼어낸다.
		entityManager.detach(auction);
		boolean extended = auction.applyBid(price, now); // 이제 메모리에서만 계산된다. DB 반영은 아래 조건부 UPDATE로만

		TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
		try {
			return writeTransaction.execute(status -> {
				// 조건부 UPDATE도 행 락을 잡는다. 다른 트랜잭션이 이 행을 잡고 있으면 그게 끝날 때까지 기다리는데,
				// 제한이 없으면 무한정 기다린다(Redis 전환 때 비관적 락과 함께 이 3초 제한이 빠졌었다).
				// 예전과 같은 기준으로 3초만 기다리고, 넘기면 아래 catch에서 503으로 바꾼다.
				entityManager.createNativeQuery(LOCK_TIMEOUT_SQL).executeUpdate();

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
		} catch (PessimisticLockingFailureException e) {
			// 3초 안에 행 락을 못 얻었다. Redis는 이 입찰이 "이겼다"고 기록했는데 DB에는 못 들어간 상태라
			// Redis가 DB보다 앞서 있다. 캐시를 지워서 다음 요청이 DB 값으로 다시 채우게 하고,
			// 사용자에게는 예전 비관적 락 때와 같은 503("잠시 후 다시 시도")을 돌려준다.
			invalidatePriceCacheQuietly(auctionId);
			throw new BidTemporarilyUnavailableException();
		} catch (RuntimeException e) {
			// 예상 못한 실패(DB 에러 등) — Redis만 "이겼다"고 알고 있고 DB엔 반영 안 된 상태로
			// 남을 수 있다. 이 트랜잭션은 이미 실패한(Postgres 기준 aborted) 상태일 수 있어서,
			// 같은 트랜잭션 안에서 DB를 다시 읽어 되돌리려 하면 그 조회마저 실패해 원래 예외를
			// 가려버릴 수 있다. 그래서 DB를 다시 읽지 않고 Redis 캐시를 비운다 — 다음 요청이
			// CACHE_MISS로 떨어지면서 새 트랜잭션에서 DB 값으로 다시 채워진다. 캐시 삭제가
			// 실패해도 원래 예외를 덮지 않도록 invalidatePriceCacheQuietly는 예외를 던지지 않는다.
			invalidatePriceCacheQuietly(auctionId);
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
		entityManager.createNativeQuery(LOCK_TIMEOUT_SQL).executeUpdate();
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

	// Redis 현재가 캐시를 지운다. 다음 입찰이 캐시 미스로 DB에서 현재가를 다시 읽어 채운다.
	//
	// 왜 커밋 "뒤"에 지우나:
	//   커밋 전에 지우면, 그 사이 들어온 입찰이 캐시 미스로 DB를 읽는데 아직 취소가 커밋되기 전이라
	//   되돌리기 전 가격(취소된 입찰가)으로 캐시가 다시 채워질 수 있다.
	// 왜 되돌린 가격으로 SET하지 않고 지우나:
	//   커밋과 SET 사이에 더 높은 입찰이 Redis에서 이겼다면, SET이 그 가격을 낮은 값으로 덮어써 버린다.
	//   지우기만 하면 다음 요청이 그 시점의 DB 값으로 채우므로 이런 역전이 없다.
	// 롤백되면 DB 현재가도 그대로라 캐시를 지울 이유가 없으므로, afterCommit에서만 실행한다.
	// 트랜잭션 밖에서 호출되면(단위 테스트 등) 바로 지운다.
	private void invalidatePriceCacheAfterCommit(Long auctionId) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					invalidatePriceCacheQuietly(auctionId);
				}
			});
		} else {
			invalidatePriceCacheQuietly(auctionId);
		}
	}

	// Redis 현재가 캐시를 지우되, 실패해도 예외를 밖으로 던지지 않는다.
	// - 입찰 취소: 취소는 이미 커밋됐으니 캐시 삭제 실패로 취소를 실패시킬 이유가 없다.
	// - 입찰 DB 반영 실패: 원래 예외(또는 503)를 그대로 돌려줘야 하므로 삭제 실패가 그걸 덮으면 안 된다.
	// 대신 이 경우 Redis 값이 DB와 달라 정상 입찰이 거절될 수 있으니 로그로 추적할 수 있게 남긴다.
	private void invalidatePriceCacheQuietly(Long auctionId) {
		try {
			priceRedisService.invalidateCurrentPrice(auctionId);
		} catch (RuntimeException e) {
			log.warn("[입찰] auctionId={} Redis 현재가 캐시 삭제 실패, 캐시가 DB와 다를 수 있음: {}",
					auctionId, e.getMessage());
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