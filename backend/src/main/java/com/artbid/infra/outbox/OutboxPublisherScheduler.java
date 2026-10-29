package com.artbid.infra.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 OutboxPublisherService를 호출만 하는 얇은 트리거.
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxPublisherService outboxPublisherService;

    // 1초마다 polling, 너무 자주 돌면 DB에 부담이 되니 과하게 줄이지 않음.
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        outboxPublisherService.publishPendingEvents();
    }
}