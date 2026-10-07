package com.artbid.infra.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AuctionSseRegistry {

	private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

	public SseEmitter subscribe(Long auctionId) {
		SseEmitter emitter = new SseEmitter(0L); // 타임아웃 0 = 무제한으로 열어둠

		// 이 auctionId를 구독하는 리스트가 아직 없으면 새로 만들고, 있으면 거기에 추가
		List<SseEmitter> auctionEmitters =
				emitters.computeIfAbsent(auctionId, id -> new CopyOnWriteArrayList<>());
		auctionEmitters.add(emitter);

		// 연결이 끝나든(정상 종료/타임아웃/에러) 리스트에서 자기 자신만 빼면 됨
		emitter.onCompletion(() -> auctionEmitters.remove(emitter));
		emitter.onTimeout(() -> auctionEmitters.remove(emitter));
		emitter.onError(e -> auctionEmitters.remove(emitter));


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
