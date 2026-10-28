package com.artbid.infra.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 아웃박스 패턴용 테이블
 * "Kafka로 보낼 이벤트"를 DB 트랜잭션 안에서 이 테이블에 같이 저장해두고,
 *   별도 OutboxPublisherService가 나중에 읽어서 Kafka로 보낸다.
 * - "DB 커밋 = 나중에 반드시 발행하겠다는 약속"이 되는 구조.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 Kafka 토픽으로 보낼지. 토픽 이름을 저장
    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    // 실제로 Kafka에 보낼 내용. JSON 문자열로 저장한다.
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

    // 재시도 횟수를 올리고, 너무 많이 실패했으면 FAILED로 바꿔서 그만 재시도하게 함
    public void markRetryOrFailed(int maxRetry) {
        this.retryCount++;
        if (this.retryCount >= maxRetry) {
            this.status = OutboxStatus.FAILED;
        }
        // maxRetry 안 넘었으면 상태는 그대로 PENDING → 다음 폴링 때 다시 시도됨
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}