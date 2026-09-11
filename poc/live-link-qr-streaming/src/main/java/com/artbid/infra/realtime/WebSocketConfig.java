package com.artbid.infra.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 호가 반영용 STOMP/WebSocket 설정.
 * 실제 backend의 infra/realtime 자리(SSE/WebSocket, 추후 다중 인스턴스 확장 시
 * Redis Pub/Sub 중계로 교체 예정)에 대응하는 PoC 구현.
 *
 * 클라이언트는 /ws-bid 로 접속(SockJS) 후 /topic/bid/{itemId} 를 구독한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-bid")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
