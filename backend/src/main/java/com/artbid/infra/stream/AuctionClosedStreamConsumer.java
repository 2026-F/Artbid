package com.artbid.infra.stream;

import com.artbid.infra.kafka.AuctionClosedMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * stream:auction.closed 를 읽는 소비자.
 *
 * 처리 흐름
 *   1) 메시지를 받으면 handle()로 처리
 *   2) 성공하면 ACK -> 그룹의 pending 목록에서 빠진다
 *   3) 실패하면 ACK 안 함 -> pending에 남는다
 *   4) 30초마다 reclaimPending()이 60초 넘게 처리 안 된 pending 메시지를 다시 가져와 처리
 *      (서버가 처리 도중 죽었을 때도 다른 서버/재시작한 서버가 이어서 처리한다)
 *   5) 5번 넘게 실패하면 dead-letter Stream으로 옮기고 ACK (무한 재시도 방지)
 *
 *  정산 생성은 다음 PR에서 옮긴다.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosedStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    static final String STREAM = RedisStreamPublisher.streamKey(AuctionClosedMessage.TOPIC);
    static final String DEAD_LETTER_STREAM = STREAM + ":dead";
    private static final Duration RECLAIM_MIN_IDLE = Duration.ofSeconds(60);
    private static final int MAX_DELIVERY_COUNT = 5;
    private static final int RECLAIM_BATCH = 50;

    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final String consumerName = RedisStreamConfig.consumerName();

    @PostConstruct
    void subscribe() {
        createGroupIfAbsent();

        StreamReadRequest<String> request = StreamReadRequest
                // lastConsumed: 그룹 기준으로 아직 아무 소비자도 안 가져간 새 메시지부터 받는다 (XREADGROUP ... >)
                .builder(StreamOffset.create(STREAM, ReadOffset.lastConsumed()))
                .consumer(Consumer.from(RedisStreamConfig.CONSUMER_GROUP, consumerName))
                // 자동 ACK를 끈다. 처리에 성공했을 때만 직접 ACK해야 실패한 메시지가 pending에 남는다.
                .autoAcknowledge(false)
                // 기본값은 "에러 한 번 나면 구독 영구 중단". Redis가 잠깐 끊겨도 계속 읽도록 끈다.
                .cancelOnError(e -> false)
                .build();

        container.register(request, this);
        container.start();
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        process(message.getId(), message.getValue());
    }

    /**
     * 오래 처리 안 된 pending 메시지를 다시 가져와 처리한다.
     * XCLAIM은 "60초 이상 놀고 있는 메시지만" 가져오므로, 서버 두 대가 동시에 돌아도 한 메시지를 한 서버만 가져간다.
     */
    @Scheduled(fixedDelay = 30_000)
    public void reclaimPending() {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(STREAM, RedisStreamConfig.CONSUMER_GROUP, Range.unbounded(), RECLAIM_BATCH);

        for (PendingMessage p : pending) {
            if (p.getElapsedTimeSinceLastDelivery().compareTo(RECLAIM_MIN_IDLE) < 0) {
                continue; // 아직 다른 소비자가 처리 중일 수 있다
            }
            if (p.getTotalDeliveryCount() >= MAX_DELIVERY_COUNT) {
                moveToDeadLetter(p.getId());
                continue;
            }
            List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream()
                    .claim(STREAM, RedisStreamConfig.CONSUMER_GROUP, consumerName, RECLAIM_MIN_IDLE, p.getId());
            for (MapRecord<String, Object, Object> record : claimed) {
                process(record.getId(), record.getValue());
            }
        }
    }

    // 처리 + 성공 시 ACK. 실패하면 ACK 안 하고 남겨둔다.
    private void process(RecordId id, Map<?, ?> fields) {
        String payload = String.valueOf(fields.get("payload"));
        try {
            handle(payload);
            ack(id);
        } catch (Exception e) {
            log.warn("[Stream] {} 처리 실패, pending에 남겨 재시도함 (id={}): {}", STREAM, id, e.getMessage());
        }
    }

    /** 실제 처리. 지금은 파이프라인 확인용으로 로그만 남긴다. */
    void handle(String payload) {
        log.info("[Stream] auction.closed 수신: {}", payload);
    }

    private void ack(RecordId id) {
        redisTemplate.opsForStream().acknowledge(STREAM, RedisStreamConfig.CONSUMER_GROUP, id);
    }

    // 계속 실패하는 메시지는 별도 Stream에 옮겨두고 원래 그룹에서는 ACK로 치운다. 나중에 사람이 확인한다.
    private void moveToDeadLetter(RecordId id) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(STREAM, Range.closed(id.getValue(), id.getValue()));
        Object payload = records.isEmpty() ? "(이미 잘려나간 메시지)" : records.get(0).getValue().get("payload");

        redisTemplate.opsForStream().add(StreamRecords.newRecord().in(DEAD_LETTER_STREAM)
                .ofMap(Map.of("originalId", id.getValue(), "payload", String.valueOf(payload))));
        ack(id);
        log.error("[Stream] {} 메시지가 {}회 실패해 dead-letter로 옮김 (id={})", STREAM, MAX_DELIVERY_COUNT, id);
    }

    /**
     * 소비자 그룹을 만든다. MKSTREAM 옵션으로 Stream이 아직 없어도 같이 만든다.
     * 이미 그룹이 있으면 Redis가 BUSYGROUP 에러를 주는데, 정상이니 무시한다.
     * ReadOffset "0": 그룹을 처음 만들 때 Stream에 이미 있던 메시지도 처음부터 받는다.
     */
    private void createGroupIfAbsent() {
        try {
            redisTemplate.execute((RedisCallback<String>) (RedisConnection connection) ->
                    connection.streamCommands().xGroupCreate(
                            STREAM.getBytes(StandardCharsets.UTF_8),
                            RedisStreamConfig.CONSUMER_GROUP,
                            ReadOffset.from("0"),
                            true));
        } catch (Exception e) {
            // 스프링 6부터 감싼 예외의 getMessage()에 원인 메시지가 안 붙어서, 가장 안쪽 원인을 꺼내 확인한다.
            String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (!cause.contains("BUSYGROUP")) {
                throw e;
            }
        }
    }
}