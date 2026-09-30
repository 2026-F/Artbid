package com.artbid.infra.realtime;

import jakarta.persistence.Id;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AuctionSseRegistry {

	// TODO: 인스턴스가 여러 대로 늘어나면 이 맵만으로는 부족 — Redis Pub/Sub으로
	// 인스턴스 간 이벤트를 중계하고, 각 인스턴스는 자기한테 붙은 연결에만 재전송해야 함
	private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

	public SseEmitter subscribe(Long auctionId) {
		SseEmitter emitter = new SseEmitter(0L); // 타임아웃 0 = 무제한으로 열어둠

		// 이 auctionId를 구독하는 리스트가 아직 없으면 새로 만들고, 있으면 거기에 추가
		List<SseEmitter> auctionEmitters =
				emitters.computeIfAbsent(auctionId, id -> new CopyOnWriteArrayList<>());
		auctionEmitters.add(emitter);

		// 연결이 끝나든(정상 종료/타임아웃/에러) 리스트에서 자기 자신만 빼면 됨
		emitter.onCompletion(() -> emitters.remove(auctionId));
		emitter.onCompletion(() -> emitters.remove(auctionId)); // 연결 끊기면 맵에서 제거
		emitter.onTimeout(() -> emitters.remove(auctionId));

		return emitter;
	}


	public void broadcast(Long auctionId, Object payload) {
		List<SseEmitter> auctionEmitters = emitters.get(auctionId);
		if (auctionEmitters == null || auctionEmitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : auctionEmitters) {
			try {
				emitter.send(payload); // 입찰 후 현재가, 마감 시각, 마감 연장 여부를 담은 레코드를 그대로 보냄 payload
			} catch (Exception e) {
				auctionEmitters.remove(emitter);
			}
		}
	}
}
