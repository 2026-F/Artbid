package com.artbid.infra.outbox;

import
import com.artbid.infra.stream.RedisStreamPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * outbox_event 테이블에서 아직 발행 안 된 이벤트를 읽어 Redis Stream으로 발행한다.
 * 주의: publishPendingEvents()는 반드시 다른 빈(OutboxPublisherScheduler)을 통해 호출해야
 * @Transactional이 걸린다 (같은 클래스 안에서 부르면 프록시를 안 거쳐서 트랜잭션이 안 걸림).
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RedisStreamPublisher streamPublisher;

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.lockPendingBatch(BATCH_SIZE);

        for (OutboxEvent event : batch) {
            try {
                // XADD는 동기 호출이라, 성공 응답을 받은 뒤에만 아래 markPublished로 넘어간다.
                streamPublisher.publish(event.getTopic(), event.getMessageKey(), event.getPayload());
                event.markPublished(LocalDateTime.now());

            } catch (Exception e) {
                 // Redis 연결 실패, 타임아웃 등. 재시도 횟수만 올리고 다음 주기(1초 뒤)에 다시 시도한다.
                // 발행은 됐는데 이 트랜잭션 커밋이 실패하면 다음 주기에 한 번 더 발행된다(중복).
                // 그래서 소비자는 같은 메시지를 두 번 받아도 안전하게 만들어야 한다(멱등성).                log.warn("Outbox 이벤트 발행 실패 (id={}, topic={}, retryCount={}): {}",
                        event.getId(), event.getTopic(), event.getRetryCount() + 1, e.getMessage());
                event.markRetryOrFailed(MAX_RETRY);
            }
        }
        // 이 메서드가 끝나면서 트랜잭션이 커밋되고, 위에서 바꾼 상태(PUBLISHED/FAILED/retryCount)가
        // 한꺼번에 저장됨.
    }
}