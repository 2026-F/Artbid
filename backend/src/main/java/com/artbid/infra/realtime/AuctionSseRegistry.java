package com.artbid.infra.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuctionSseRegistry {

	// TODO: 인스턴스가 여러 대로 늘어나면 이 맵만으로는 부족 — Redis Pub/Sub으로
	// 인스턴스 간 이벤트를 중계하고, 각 인스턴스는 자기한테 붙은 연결에만 재전송해야 함
	private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitter subscribe(Long auctionId) {
		SseEmitter emitter = new SseEmitter(0L); // 타임아웃 0 = 무제한으로 열어둠
		emitters.put(auctionId, emitter); // auctionId랑 이 연결을 짝지어 저장
		emitter.onCompletion(() -> emitters.remove(auctionId)); // 연결 끊기면 맵에서 제거
		emitter.onTimeout(() -> emitters.remove(auctionId));
		return emitter;
	}


	public void broadcast(Long auctionId, Object payload) {
		SseEmitter emitter = emitters.get(auctionId);
		if (emitter == null) {
			return;
		}
		try {
			emitter.send(payload); // 입찰 후 현재가, 마감 시각, 마감 연장 여부를 담은 레코드를 그대로 보냄 payload
		} catch (Exception e) {
			emitters.remove(auctionId);
		}
	}
}
