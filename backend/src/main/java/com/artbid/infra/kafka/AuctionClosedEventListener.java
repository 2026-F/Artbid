package com.artbid.infra.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 지금은 "파이프라인이 제대로 도는지"만 확인하는 뼈대(skeleton)입니다.
 * 실제로 여기서 Settlement를 만들거나 작품 상태를 바꾸는 로직은,
 * 이 파이프라인이 로컬에서 안정적으로 동작하는 걸 확인한 다음에
 * 별도 작업으로 옮길 예정입니다 (기존 AuctionClosingService의 동기 로직은
 * 지금 그대로 유지 — 혹시 여기서 문제가 생겨도 실제 서비스엔 영향 없음).
 */
@Slf4j
@Component
public class AuctionClosedEventListener {

    @KafkaListener(topics = AuctionClosedMessage.TOPIC, groupId = "artbid-backend")
    public void consume(String payload) {
        // TODO: 지금은 로그만 남김. 파이프라인 검증 끝나면 여기서
        //   1) 이미 처리된 auctionId인지 확인(멱등성)
        //   2) Settlement 생성 + Artwork.markSold() 로 로직 이전
        log.info("[Kafka] auction.closed 수신: {}", payload);
    }
}