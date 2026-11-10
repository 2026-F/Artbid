package com.artbid.auction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AuctionPriceRedisService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> updateCurrentPriceScript;

    private static final long CACHE_MISS = -1L;
    private static final long UPDATED = 1L;

    public enum UpdateResult { SUCCESS, REJECTED, CACHE_MISS }

    /**
     * Redis에서 "이 입찰가가 이길 수 있는지"를 원자적으로 확인하고,
     * 이기는 경우에 한해 현재가를 바로 갱신한다.
     */
    public UpdateResult tryUpdateCurrentPrice(Long auctionId, Long newPrice, Long minBidUnit) {
        String key = currentPriceKey(auctionId);

        Long result = redisTemplate.execute(
                updateCurrentPriceScript,
                Collections.singletonList(key),
                String.valueOf(newPrice),
                String.valueOf(minBidUnit)
        );

        if (result == null) {
            throw new IllegalStateException("Redis 스크립트 실행 결과가 null입니다. Redis 연결을 확인하세요.");
        }
        if (result == CACHE_MISS) return UpdateResult.CACHE_MISS;
        if (result == UPDATED) return UpdateResult.SUCCESS;
        return UpdateResult.REJECTED;
    }

    /** DB에서 읽어온 현재가로 Redis 캐시를 채운다 (캐시 미스 복구용) */
    public void primeCurrentPrice(Long auctionId, Long currentPriceFromDb) {
        // setIfAbsent: 이미 다른 요청이 먼저 채워놨으면 덮어쓰지 않는다
        redisTemplate.opsForValue().setIfAbsent(currentPriceKey(auctionId), String.valueOf(currentPriceFromDb));
    }

    private String currentPriceKey(Long auctionId) {
        return "auction:" + auctionId + ":current_price";
    }
}