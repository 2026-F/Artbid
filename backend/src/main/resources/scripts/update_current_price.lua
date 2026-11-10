-- KEYS[1] = Redis에 저장된 "현재가" 키 (예: auction:22:current_price)
-- ARGV[1] = 이번에 들어온 입찰가
-- ARGV[2] = 최소 입찰 단위 (min_bid_unit)
--
-- 이 스크립트 전체는 Redis 안에서 끊기지 않고(원자적으로) 실행된다.
-- 그래서 "현재가 읽기 -> 비교 -> 쓰기"를 세 번 왕복하지 않고 한 번에 끝낼 수 있다.

local currentPriceStr = redis.call('GET', KEYS[1])

-- 아직 Redis에 캐시된 값이 없는 경우 (서버 재시작 직후 등)
if currentPriceStr == false then
    return -1  -- "캐시 없음" 신호. 호출하는 쪽에서 DB 값으로 채워야 한다.
end

local currentPrice = tonumber(currentPriceStr)
local newPrice = tonumber(ARGV[1])
local minBidUnit = tonumber(ARGV[2])

if newPrice >= currentPrice + minBidUnit then
    redis.call('SET', KEYS[1], newPrice)
    return 1   -- 성공: 현재가 갱신됨
else
    return 0   -- 실패: 입찰가가 너무 낮음
end