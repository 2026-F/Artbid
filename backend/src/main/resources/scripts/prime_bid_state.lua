-- DB에서 읽은 경매 상태로 입찰 판정용 해시를 채운다 (캐시 미스 복구용).
--
-- KEYS[1] = auction:{id}:bid_state
-- ARGV[1] = 현재가, ARGV[2] = 최소 단위, ARGV[3] = 상태, ARGV[4] = 마감(epoch ms), ARGV[5] = 만료(초)
--
-- 이미 키가 있으면 아무것도 안 하고 0을 돌려준다. 여러 요청이 동시에 캐시 미스를 겪어도
-- 먼저 채운 값(그 뒤로 Redis에서 이긴 입찰이 반영됐을 수도 있는 값)을 DB의 옛 값으로 덮어쓰지 않기 위해서다.
-- EXISTS와 HSET을 한 스크립트로 묶어야 그 사이에 다른 요청이 끼어들지 못한다.

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

redis.call('HSET', KEYS[1], 'price', ARGV[1], 'unit', ARGV[2], 'status', ARGV[3], 'end_ms', ARGV[4])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
return 1
