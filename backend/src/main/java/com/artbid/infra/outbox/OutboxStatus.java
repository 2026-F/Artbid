package com.artbid.infra.outbox;

public enum OutboxStatus {
    PENDING,    // 아직 발행 안 됨
    PUBLISHED,  // Kafka 발행 성공
    FAILED      // 재시도 다 했는데도 실패 — 수동 확인 필요
}