package com.artbid.liveshow.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 라이브 편성 단위 SSE 구독(GET /ws/live-shows/{showId}). 등록과 해제는 AuctionSseRegistry와 같은 방식이다.
 * 구독은 서버 인스턴스 메모리에 있어서 인스턴스가 여러 대면 다른 인스턴스의 구독자에게는 전달되지 않는다(경매 SSE와 같음).
 */
@Slf4j
@Component
public class LiveShowSseRegistry {

	private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

	public SseEmitter subscribe(Long showId) {
		SseEmitter emitter = new SseEmitter(0L); // 타임아웃 0 = 무제한

		// 추가와 빈 목록 정리(unregister)가 같은 키에서 엇갈리지 않게 compute 안에서 추가한다.
		emitters.compute(showId, (id, list) -> {
			List<SseEmitter> showEmitters = list == null ? new CopyOnWriteArrayList<>() : list;
			showEmitters.add(emitter);
			return showEmitters;
		});

		// 연결이 끝나면(정상 종료, 타임아웃, 오류) 목록에서 빼고, 구독자가 없으면 라이브 키도 지운다.
		Runnable remove = () -> unregister(showId, emitter);
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(e -> remove.run());
		return emitter;
	}

	/** 이름 있는 이벤트(event: 이름)로 그 라이브의 모든 구독자에게 보낸다. 보내기에 실패한 구독은 정리한다. */
	public void broadcastEvent(Long showId, String eventName, Object payload) {
		List<SseEmitter> showEmitters = emitters.get(showId);
		if (showEmitters == null || showEmitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : showEmitters) {
			if (!sendEvent(emitter, eventName, payload)) {
				unregister(showId, emitter);
			}
		}
	}

	/** 구독 하나에만 보낸다. 실패하면(연결이 이미 끊김 등) false. */
	public boolean sendEvent(SseEmitter emitter, String eventName, Object payload) {
		try {
			emitter.send(SseEmitter.event().name(eventName).data(payload));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/** 테스트와 운영 확인용. 그 라이브의 현재 구독 수 */
	public int subscriberCount(Long showId) {
		List<SseEmitter> showEmitters = emitters.get(showId);
		return showEmitters == null ? 0 : showEmitters.size();
	}

	/** 구독 하나를 뺀다. 그 라이브의 구독자가 없으면 키도 지운다. */
	public void unregister(Long showId, SseEmitter emitter) {
		emitters.computeIfPresent(showId, (id, list) -> {
			list.remove(emitter);
			return list.isEmpty() ? null : list;
		});
	}
}
