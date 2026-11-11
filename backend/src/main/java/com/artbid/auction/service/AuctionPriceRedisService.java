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

    /**
     * DB 반영이 실패했을 때 Redis 캐시를 아예 비운다 (보정용). DB를 다시 읽어서 되돌리려
     * 하면, 하필 DB 에러로 실패한 그 트랜잭션은 이미 aborted 상태라 그 안에서의 재조회도
     * 같이 실패해 원래 예외를 가릴 수 있다. 그래서 그냥 비워두면, 다음 요청이 자연스럽게
     * CACHE_MISS로 떨어지면서 완전히 새로운(깨끗한) 트랜잭션에서 DB 값으로 다시 채워진다.
     */
    public void invalidateCurrentPrice(Long auctionId) {
        redisTemplate.delete(currentPriceKey(auctionId));
    }

    private String currentPriceKey(Long auctionId) {
        return "auction:" + auctionId + ":current_price";
    }
}