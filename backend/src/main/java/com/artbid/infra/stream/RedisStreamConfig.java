package com.artbid.infra.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Configuration
public class RedisStreamConfig {

    // 소비자 그룹: 같은 그룹 안의 소비자들은 메시지를 "나눠서" 받는다.
    // 서버가 2대가 돼도 auction.closed 하나는 한 서버만 처리한다 (정산 중복 방지).
    public static final String CONSUMER_GROUP = "artbid-backend";

    /**
     * Stream을 계속 읽어서 등록된 리스너에게 넘겨주는 컨테이너.
     * 내부적으로 "새 메시지 올 때까지 최대 2초 기다리기(XREADGROUP BLOCK)"를 반복한다.
     * 이 기다리기는 입찰 판정과 다른 전용 연결에서 돌아서, 입찰 Lua 실행을 막지 않는다.
     */
    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1)) // 한 번에 최대 2초 기다림
                        .batchSize(10)                      // 한 번에 최대 10건 가져옴
                        .errorHandler(e -> log.warn("[Stream] 읽기 실패: {}", e.getMessage()))
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    /**
     * 그룹 안에서 이 서버를 구분하는 이름. 서버마다 달라야 한다.
     * 도커 컨테이너는 HOSTNAME이 컨테이너마다 다르게 들어가서 그걸 쓴다.
     */
    public static String consumerName() {
        String host = System.getenv("HOSTNAME");
        return (host == null || host.isBlank()) ? "consumer-" + UUID.randomUUID() : host;
    }
}