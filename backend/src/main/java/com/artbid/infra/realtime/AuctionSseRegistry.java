package com.artbid.infra.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

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


	// 이름 있는 SSE 이벤트(event: 이름)로 보낸다. 브라우저 EventSource의 onmessage는 이름 없는 이벤트만 받으므로
	// 기존 broadcast()를 쓰는 화면에는 영향이 없고, 새 이벤트는 addEventListener(이름)로 받는다.
	public void broadcastEvent(Long auctionId, String eventName, Object payload) {
		List<SseEmitter> auctionEmitters = emitters.get(auctionId);
		if (auctionEmitters == null || auctionEmitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : auctionEmitters) {
			if (!sendEvent(emitter, eventName, payload)) {
				auctionEmitters.remove(emitter);
			}
		}
	}

	// 구독 하나에만 이름 있는 이벤트를 보낸다. 실패하면(연결이 이미 끊김 등) false.
	public boolean sendEvent(SseEmitter emitter, String eventName, Object payload) {
		try {
			emitter.send(SseEmitter.event().name(eventName).data(payload));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// 지금 구독자가 한 명 이상 있는 경매 ID. 주기적 동기화는 이 경매들에만 보낸다.
	public Set<Long> activeAuctionIds() {
		return emitters.entrySet().stream()
				.filter(entry -> !entry.getValue().isEmpty())
				.map(Map.Entry::getKey)
				.collect(Collectors.toUnmodifiableSet());
	}

	// broadcast()를 DB 트랜잭션이 진짜로 커밋된 뒤에만 실행되게 감싸주는 버전.
	// closeOne()이 트랜잭션 도중 뭔가 실패해서 롤백되면, 경매는 사실 안 닫힌 건데
	// 사용자한테는 "마감됐다"는 알림이 먼저 나가버리는 상황을 막기 위함.
	public void broadcastAfterCommit(Long auctionId, Object payload){
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					broadcast(auctionId, payload);
				}
			});
		} else {
			broadcast(auctionId, payload);
		}
	}
}
