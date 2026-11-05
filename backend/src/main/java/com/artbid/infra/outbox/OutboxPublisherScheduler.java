package com.artbid.infra.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 OutboxPublisherService를 호출만 하는 얇은 트리거.
 * Kafka를 쓰지 않는 동안은 꺼둔다. 켜둔 채 브로커에 연결할 수 없으면 발행 대기가 단일 스케줄러 스레드를 막아
 * 경매 마감 같은 다른 스케줄 작업까지 밀린다.
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