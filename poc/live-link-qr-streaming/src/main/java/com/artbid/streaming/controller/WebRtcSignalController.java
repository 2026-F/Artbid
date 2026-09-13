package com.artbid.streaming.controller;

import com.artbid.streaming.domain.SignalMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * 노트북 카메라(방송자) <-> 폰(시청자) 간 WebRTC 연결을 맺기 위한 시그널링 중계.
 * 서버는 SDP/ICE 메시지의 내용을 해석하지 않고, role에 따라 그대로 전달만 한다.
 * 실제 영상 스트림은 이 서버를 거치지 않고 브라우저끼리 P2P로 직접 오간다.
 *
 * - 시청자(role=viewer) -> 항상 "/topic/webrtc/{itemId}/broadcaster" 로 전달 (join, answer, ice)
 * - 방송자(role=broadcaster) -> 항상 해당 시청자 전용 채널
 *   "/topic/webrtc/{itemId}/viewer/{viewerId}" 로 전달 (offer, ice)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebRtcSignalController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/webrtc/{itemId}/signal")
    public void signal(@DestinationVariable String itemId, SignalMessage message) {
        log.debug("[WebRTC] itemId={}, role={}, type={}, viewerId={}",
                itemId, message.getRole(), message.getType(), message.getViewerId());

        String destination = "broadcaster".equals(message.getRole())
                ? "/topic/webrtc/" + itemId + "/viewer/" + message.getViewerId()
                : "/topic/webrtc/" + itemId + "/broadcaster";

        messagingTemplate.convertAndSend(destination, message);
    }
}
