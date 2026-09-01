package com.artbid.infra.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidEventProducer {

	public static final String BID_EVENTS_TOPIC = "bid-events";

	private final KafkaTemplate<String, String> kafkaTemplate;

	/**
	 * 파티션 키 = 경매 ID → 같은 경매 내 입찰 순서만 보장하고, 여러 경매는 병렬 처리.
	 */
	public void publishBidEvent(Long auctionId, String payload) {
		kafkaTemplate.send(BID_EVENTS_TOPIC, String.valueOf(auctionId), payload);
	}
}
