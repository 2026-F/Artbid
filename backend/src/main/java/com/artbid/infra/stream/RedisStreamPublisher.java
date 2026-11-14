package com.artbid.infra.stream;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisStreamPublisher {

    public static final String STREAM_PREFIX = "stream:";

    static final long MAX_LEN = 10_000;

    private final StringRedisTemplate redisTemplate;

    public RecordId publish(String topic, String messagekey, String payload){
        String stream = streamKey(topic);

        Map<String, String> fields = new HashMap<>();
        fields.put("key", messagekey == null ? "" : messagekey);
        fields.put("payload", payload);

        RecordId id = redisTemplate.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(fields));
        if (id == null) {
            throw new IllegalStateException("Redis Stream 발행 결과가 비어 있습니다: " + stream);
        }

        redisTemplate.opsForStream().trim(stream, MAX_LEN, true);
        return id;
    }

    public static String streamKey(String topic) {
        return STREAM_PREFIX + topic;
    }
}
