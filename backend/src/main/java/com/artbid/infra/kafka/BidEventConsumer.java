package com.artbid.infra.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BidEventConsumer {

	@KafkaListener(topics = BidEventProducer.BID_EVENTS_TOPIC, groupId = "artbid-backend")
	public void consume(String payload) {
		// TODO: MySQL에 입찰 이력 영구 기록 (Bid 엔티티 저장)
	}
}
