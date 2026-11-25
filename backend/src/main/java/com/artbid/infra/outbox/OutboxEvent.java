package com.artbid.infra.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 아웃박스 패턴용 테이블
 * "Redis Stream으로 보낼 이벤트"를 DB 트랜잭션 안에서 이 테이블에 같이 저장해두고,
 *   별도 OutboxPublisherService가 나중에 읽어서 Redis Stream(stream:{topic})으로 보낸다.
 * - "DB 커밋 = 나중에 반드시 발행하겠다는 약속"이 되는 구조.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final long MAX_BACKOFF_SECONDS = 300; // 5분

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 Stream으로 보낼지. 토픽 이름을 저장 (Stream 키는 "stream:" + topic)
    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    // 실제로 Stream에 보낼 내용. JSON 문자열로 저장한다.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    // 발행 실패해서 재시도한 횟수. 일정 횟수 넘으면 FAILED로 바꿔서 무한 재시도 방지.
    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    // 다음 발행 시도 가능 시각. null이면 바로 시도한다.
    // 실패할 때마다 1초, 2초, 4초 ... 최대 5분 뒤로 미뤄서, Redis가 몇 분 멈춰도 이벤트가 영구 실패(FAILED)로 끝나지 않게 한다.
    private LocalDateTime nextRetryAt;

    public static OutboxEvent create(String topic, String messageKey, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.topic = topic;
        event.messageKey = messageKey;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;
        return event;
    }

    public void markPublished(LocalDateTime now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /**
     * 발행 실패를 기록한다. 너무 많이 실패했으면 FAILED로 바꿔 그만 재시도하고,
     * 아니면 다음 시도 시각을 실패 횟수에 따라 늘린다(지수 백오프).
     *   1번째 실패 → 1초 뒤, 2번째 → 2초, 3번째 → 4초, ... 최대 5분
     * 1초마다 바로 다시 두드리면, Redis가 잠깐 멈춘 사이에 5번을 다 써버려 이벤트가 영구 실패로 끝난다.
     */
    public void markRetryOrFailed(int maxRetry, LocalDateTime now) {
        this.retryCount++;
        if (this.retryCount >= maxRetry) {
            this.status = OutboxStatus.FAILED;
            return;
        }
        long delaySeconds = Math.min(1L << (this.retryCount - 1), MAX_BACKOFF_SECONDS);
        this.nextRetryAt = now.plusSeconds(delaySeconds);
        // 상태는 그대로 PENDING → nextRetryAt이 지나면 다음 폴링 때 다시 시도된다
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}