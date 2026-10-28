package com.artbid.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 실제로 outbox_event 테이블을 읽어서 Kafka로 발행하는 역할.
 * 주의: 이 클래스의 publishPendingEvents()는 반드시 "다른 빈(bean)"을 통해서
 * 호출되어야 @Transactional이 제대로 걸림. 스케줄러(OutboxPublisherScheduler)를
 * 별도 클래스로 분리해서, 거기서 이 빈을 호출하게 만들었음.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.lockPendingBatch(BATCH_SIZE);

        for (OutboxEvent event : batch) {
            try {
                // Kafka 발행은 성공했는데 DB에 published=true 저장은 못 하는 상황이 생길 수 있음.
                // .get()으로 기다리면, 발행 성공 확인 -> 같은 트랜잭션에서 markPublished()까지
                // 한 묶음으로 처리할 수 있음.
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);

                event.markPublished(LocalDateTime.now());

            } catch (Exception e) {
                // 네트워크 오류, 타임아웃 등 — 발행 실패. 재시도 횟수만 올리고 다음 폴링에 다시 시도.
                log.warn("Outbox 이벤트 발행 실패 (id={}, topic={}, retryCount={}): {}",
                        event.getId(), event.getTopic(), event.getRetryCount() + 1, e.getMessage());
                event.markRetryOrFailed(MAX_RETRY);
            }
        }
        // 이 메서드가 끝나면서 트랜잭션이 커밋되고, 위에서 바꾼 상태(PUBLISHED/FAILED/retryCount)가
        // 한꺼번에 저장됨.
    }
}