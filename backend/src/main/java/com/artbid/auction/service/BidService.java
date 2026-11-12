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

	// 입찰 판정(상태·마감·가격)은 전부 Redis Lua 스크립트(bid.lua) 한 번으로 끝낸다.
	// DB는 이긴 입찰(WIN)만 건드린다 — 조건부 UPDATE 한 번 + 입찰 INSERT 한 번.
	// 지는 입찰(TOO_LOW / NOT_BIDDABLE / ENDED)은 DB 커넥션을 아예 빌리지 않는다.
	//
	// 예전에는 상태·마감 검사를 위해 입찰마다 findById를 했다. 한 경매에 1,000명이 몰리면
	// 대부분이 지는 입찰인데도 전부 커넥션을 빌려 SELECT를 했고, 그게 커넥션 풀 대기와
	// DB CPU의 큰 몫이었다.
	//
	// 이 메서드 전체를 @Transactional로 감싸지 않는 이유는 그대로다. 감싸면 Redis를 기다리는
	// 동안에도 커넥션을 쥐고 있게 된다. 쓰기가 필요한 순간에만 TransactionTemplate으로 짧게 연다.
	public BidResult submitBid(Long auctionId, Long bidderId, Long price) {
		LocalDateTime now = LocalDateTime.now();

		AuctionPriceRedisService.BidDecision decision = priceRedisService.tryBid(auctionId, price, now);

		if (decision.outcome() == AuctionPriceRedisService.Outcome.CACHE_MISS) {
			// 캐시가 비어 있을 때만(서버 재시작 직후, TTL 만료, 상태 변경으로 비운 직후) DB를 한 번 읽는다.
			// 이 조회는 트랜잭션 밖이라 JPA가 이 호출 하나만을 위한 짧은 트랜잭션을 열었다 바로 커넥션을 돌려준다.
			Auction auction = auctionRepository.findById(auctionId)
					.orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId));
			priceRedisService.prime(auction);
			decision = priceRedisService.tryBid(auctionId, price, now);

			if (decision.outcome() == AuctionPriceRedisService.Outcome.CACHE_MISS) {
				// 채운 직후 다른 요청의 실패 보정(invalidate)으로 또 비워진 아주 드문 경우. 무한 반복하지 않고 재시도를 안내한다.
				throw new BidTemporarilyUnavailableException();
			}
		}

		switch (decision.outcome()) {
			case NOT_BIDDABLE -> throw new InvalidBidException("진행 중인 경매가 아닙니다. 현재 상태: " + decision.status());
			case ENDED -> throw new InvalidBidException("이미 마감된 경매입니다.");
			case TOO_LOW -> throw new InvalidBidException(
					"입찰가가 너무 낮습니다. 현재가 " + decision.currentPrice() + "원, 최소 "
							+ (decision.currentPrice() + decision.minBidUnit()) + "원 이상 입력하세요.");
			default -> {
				// WIN: 아래에서 DB에 반영
			}
		}

		return saveWinningBid(auctionId, bidderId, price, now, decision);
	}

	// Redis에서 이긴 입찰을 DB에 반영한다. Redis에는 이미 새 현재가(필요하면 연장된 마감)가 들어가 있다.
	private BidResult saveWinningBid(Long auctionId, Long bidderId, Long price, LocalDateTime now,
									 AuctionPriceRedisService.BidDecision decision) {
		TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
		try {
			return writeTransaction.execute(status -> {
				// 조건부 UPDATE도 행 락을 잡는다. 다른 트랜잭션(마감, 입찰 취소, 관리자 상태 변경)이 이 행을 잡고 있으면
				// 끝날 때까지 기다리는데, 제한이 없으면 무한정 기다린다. 3초만 기다리고 넘기면 아래 catch에서 503.
				entityManager.createNativeQuery(LOCK_TIMEOUT_SQL).executeUpdate();

				int updatedRows = auctionRepository.updateIfHigherPrice(
						auctionId, price, decision.auctionEndAt(), decision.status());

				if (updatedRows == 0) {
					// Redis에서는 이겼지만 DB에 반영하는 그 짧은 틈에
					//  (1) 더 높은 가격의 입찰이 먼저 커밋됐거나 (Redis 판정 순서와 커밋 순서가 뒤바뀐 경우)
					//  (2) 관리자 상태 변경이나 마감으로 DB 상태가 진행 중이 아니게 된 경우다.
					// (1)이면 Redis에는 이미 더 높은 가격이 있으니 맞는 상태다. 여기서 캐시를 지우면, 아직 커밋 안 된
					// 다른 승리 입찰들보다 낮은 DB 값으로 다시 채워져 오히려 어긋나므로 지우지 않는다.
					// (2)는 그 변경이 커밋된 뒤 AuctionBidCacheInvalidator가 캐시를 지운다.
					// 이 요청만 "잠시 후 다시 시도"로 돌려준다.
					throw new BidTemporarilyUnavailableException();
				}

				bidRepository.save(new Bid(auctionId, bidderId, price, now));

				BidResult result = new BidResult(price, decision.auctionEndAt(), decision.extended());
				broadcastAfterCommit(auctionId, result);
				publishLiveChanged(winningState(auctionId, price, decision), now, AuctionLiveReason.BID, decision.extended());
				return result;
			});

		} catch (BidTemporarilyUnavailableException e) {
			throw e; // 위 설명대로 캐시를 건드리지 않는다
		} catch (PessimisticLockingFailureException e) {
			// 3초 안에 행 락을 못 얻었다. Redis는 이 입찰이 이겼다고 기록했는데 DB엔 못 들어갔으니
			// Redis가 DB보다 앞서 있다. 캐시를 지워서 다음 요청이 DB 값으로 다시 채우게 하고 503을 돌려준다.
			invalidatePriceCacheQuietly(auctionId);
			throw new BidTemporarilyUnavailableException();
		} catch (RuntimeException e) {
			// 예상 못한 실패(DB 에러 등). 마찬가지로 Redis만 이겼다고 알고 있으니 캐시를 지운다.
			// 같은 트랜잭션 안에서 DB를 다시 읽어 되돌리려 하면 이미 aborted 상태라 그 조회마저 실패해
			// 원래 예외를 가릴 수 있어서, 읽지 않고 지우기만 한다. 삭제가 실패해도 원래 예외는 그대로 던진다.
			invalidatePriceCacheQuietly(auctionId);
			throw e;
		}
	}

	// 실시간 상태 이벤트(AuctionLiveChangedEvent)에 실을 값만 채운 경매 객체.
	// 이긴 입찰 뒤의 상태는 Redis 판정 결과에 다 들어 있으니 DB를 다시 읽지 않는다. 저장하지 않는 임시 객체다.
	private Auction winningState(Long auctionId, Long price, AuctionPriceRedisService.BidDecision decision) {
		return Auction.builder()
				.id(auctionId)
				.currentPrice(price)
				.minBidUnit(decision.minBidUnit())
				.auctionEndAt(decision.auctionEndAt())
				.status(decision.status())
				.build();
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

	// Redis 입찰 판정 캐시를 지운다. 다음 입찰이 캐시 미스로 DB에서 다시 읽어 채운다.
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
			priceRedisService.invalidate(auctionId);
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