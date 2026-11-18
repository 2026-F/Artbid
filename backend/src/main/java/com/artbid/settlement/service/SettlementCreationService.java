package com.artbid.settlement.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.event.AuctionClosedMessage;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.settlement.domain.Settlement;
import com.artbid.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artbid.notification.domain.NotificationType;
import com.artbid.notification.service.NotificationRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매 마감 이벤트를 받아 정산을 만들고 작품을 판매완료로 바꾼다.
 *
 * 같은 이벤트가 두 번 와도 정산은 하나만 생겨야 한다(멱등성). 두 번 오는 경우:
 *   - 발행은 됐는데 outbox의 "발행됨" 표시 커밋이 실패해서 다음 주기에 다시 발행될 때
 *   - 소비자가 처리하다 죽어서 다른 소비자가 pending 메시지를 다시 가져갈 때
 * 그래서 두 겹으로 막는다.
 *   1) 이미 이 경매의 정산이 있으면 아무것도 안 한다 (평소에는 여기서 걸러진다)
 *   2) 두 소비자가 정말 동시에 1)을 통과해도, settlement.auction_id 유니크 제약이 두 번째 INSERT를 막는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCreationService {

    private static final double PREMIUM_RATE = 0.10;      // 낙찰가의 10%를 수수료로
    private static final long SHIPPING_FEE = 30_000L;
    private static final long PAYMENT_DEADLINE_DAYS = 3;
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final SettlementRepository settlementRepository;
    private final ArtworkRepository artworkRepository;
    private final AuctionRepository auctionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 정산을 새로 만들었으면 true, 이미 있거나 만들 필요가 없으면 false. */
    @Transactional
    public boolean createForClosedAuction(AuctionClosedMessage message) {
        Long auctionId = message.auctionId();

        if (settlementRepository.existsByAuctionId(auctionId)) {
            log.info("[정산 생성] auctionId={} 이미 정산이 있어 건너뜀", auctionId);
            return false;
        }

        // 이벤트가 늦게 처리되는 사이 관리자가 이 작품으로 경매를 다시 열었다면, 옛 경매의 정산을 만들면 안 된다.
        // (다시 열린 경매가 진행 중인데 작품이 SOLD로 바뀌어 버린다.) 더 최근 경매가 있으면 건너뛴다.
        if (isSuperseded(message.artworkId(), auctionId)) {
            log.warn("[정산 생성] auctionId={} 같은 작품의 더 최근 경매가 있어 건너뜀", auctionId);
            return false;
        }

        LocalDateTime closedAt = message.closedAt() != null ? message.closedAt() : LocalDateTime.now();
        long finalPrice = message.finalPrice();
        long premiumFee = Math.round(finalPrice * PREMIUM_RATE);

        Settlement settlement = Settlement.create(
                auctionId, message.bidderId(), finalPrice,
                premiumFee, SHIPPING_FEE, closedAt.plusDays(PAYMENT_DEADLINE_DAYS));
        // saveAndFlush: INSERT를 지금 바로 날려서, 유니크 제약 위반이 커밋 때가 아니라 여기서 터지게 한다.
        settlementRepository.saveAndFlush(settlement);

        Artwork artwork = artworkRepository.findById(message.artworkId())
                .orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다: " + message.artworkId()));
        artwork.markSold();

        // 낙찰자에게 알림. 이 트랜잭션이 커밋된 뒤에 만들어진다(NotificationEventListener).
        // 정산을 새로 만들었을 때만 여기까지 오므로, 이벤트가 중복으로 와도 알림은 한 번만 간다.
        notifyWinner(message, artwork, settlement);

        log.info("[정산 생성] auctionId={} settlementId={} 생성", auctionId, settlement.getId());
        return true;
    }

    @Transactional(readOnly = true)
    public boolean exists(Long auctionId) {
        return settlementRepository.existsByAuctionId(auctionId);
    }

    private boolean isSuperseded(Long artworkId, Long auctionId) {
        return auctionRepository.findByArtworkIdIn(List.of(artworkId)).stream()
                .anyMatch(other -> other.getId() > auctionId);
    }

    private void notifyWinner(AuctionClosedMessage message, Artwork artwork, Settlement settlement) {
        eventPublisher.publishEvent(new NotificationRequestedEvent(
                message.bidderId(),
                NotificationType.AUCTION_WON,
                "낙찰되었습니다",
                "'%s' 작품을 %,d원에 낙찰받았어요. %s까지 결제해 주세요."
                        .formatted(artwork.getTitle(), message.finalPrice(),
                                settlement.getPaymentDeadlineAt().format(DEADLINE_FORMAT)),
                "/mypage"));
    }
}