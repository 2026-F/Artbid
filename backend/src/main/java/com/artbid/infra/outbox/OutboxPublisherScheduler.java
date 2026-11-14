package com.artbid.infra.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1초마다 OutboxPublisherService를 호출하는 얇은 트리거.
 * app.outbox.publisher.enabled=true일 때만 켜진다 (배포 서버는 OUTBOX_PUBLISHER_ENABLED 환경변수로 켬).
 */
@Component
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxPublisherService outboxPublisherService;

    // 1초마다 polling, 너무 자주 돌면 DB에 부담이 되니 과하게 줄이지 않음.
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        outboxPublisherService.publishPendingEvents();
    }
}