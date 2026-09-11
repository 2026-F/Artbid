package com.artbid.auction.service;

import com.artbid.auction.domain.BidItem;
import com.artbid.auction.domain.BidUpdateMessage;
import com.artbid.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 경매 아이템 상태 관리 + 실시간 호가 브로드캐스트.
 * DB 없이 메모리에만 보관하는 PoC 용 서비스. (실 서비스의 auction.service 자리에 대응,
 * Redis Lua compare-and-set / 안티 스나이핑 로직은 이 PoC의 검증 범위가 아니다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final AppProperties appProperties;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;

    private final Map<String, BidItem> items = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> runningSimulations = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        appProperties.getDemoItems().forEach((itemId, cfg) -> {
            items.put(itemId, new BidItem(itemId, cfg.getTitle(), cfg.getYoutubeVideoId(), cfg.getInitialPrice()));
            log.info("데모 아이템 로드: {} (title={}, initialPrice={})", itemId, cfg.getTitle(), cfg.getInitialPrice());
        });
    }

    public Optional<BidItem> findItem(String itemId) {
        return Optional.ofNullable(items.get(itemId));
    }

    /** 수동으로 호가를 올리고 즉시 브로드캐스트 (버튼 클릭 등으로 호출) */
    public BidItem placeManualBid(String itemId, long increment) {
        BidItem item = requireItem(itemId);
        item.addBid(increment);
        broadcast(item, "MANUAL");
        return item;
    }

    /**
     * 다른 시청자들이 계속 입찰하는 상황을 흉내 내는 자동 시뮬레이터를 시작한다.
     * 이미 실행 중이면 아무것도 하지 않는다(멱등). 페이지 진입 시 자동 호출된다.
     */
    public void startAutoSimulation(String itemId) {
        requireItem(itemId);
        runningSimulations.computeIfAbsent(itemId, id ->
                taskScheduler.scheduleAtFixedRate(() -> tick(id), Duration.ofSeconds(3))
        );
    }

    public void stopAutoSimulation(String itemId) {
        ScheduledFuture<?> future = runningSimulations.remove(itemId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void tick(String itemId) {
        BidItem item = items.get(itemId);
        if (item == null) {
            stopAutoSimulation(itemId);
            return;
        }
        long increment = ThreadLocalRandom.current().nextLong(1_000, 10_000);
        item.addBid(increment);
        broadcast(item, "AUTO_SIM");
    }

    private void broadcast(BidItem item, String source) {
        BidUpdateMessage message = new BidUpdateMessage(
                item.getItemId(), item.getCurrentPrice(), item.getBidCount(), source);
        messagingTemplate.convertAndSend("/topic/bid/" + item.getItemId(), message);
        log.debug("브로드캐스트: itemId={}, price={}, source={}", item.getItemId(), item.getCurrentPrice(), source);
    }

    private BidItem requireItem(String itemId) {
        BidItem item = items.get(itemId);
        if (item == null) {
            throw new IllegalArgumentException("존재하지 않는 itemId: " + itemId);
        }
        return item;
    }
}
