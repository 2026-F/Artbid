-- 입찰 한 건을 Redis 안에서 "끝까지" 판정한다. DB는 이긴 입찰(WIN)만 건드린다.
--
-- KEYS[1] = auction:{id}:bid_state  (해시: price, unit, status, end_ms)
-- ARGV[1] = 이번 입찰가
-- ARGV[2] = 지금 시각 (epoch 밀리초)
-- ARGV[3] = 마감 연장 기준 (초)  -- 마감까지 이 시간 이하로 남았을 때 들어온 입찰이면 연장
-- ARGV[4] = 연장 시간 (초)
-- ARGV[5] = 키 만료 시간 (초)
--
-- 돌려주는 값: { 결과코드, 현재가, 최소단위, 상태, 마감(epoch ms), 연장여부("1"/"0") }
--   결과코드: MISS / NOT_BIDDABLE / ENDED / TOO_LOW / WIN
--   (MISS면 첫 칸만 있다. 호출하는 쪽이 DB에서 읽어 prime_bid_state.lua로 채운 뒤 다시 부른다.)
--
-- 스크립트 전체가 Redis 안에서 끊기지 않고(원자적으로) 실행되므로
-- "상태 읽기 -> 상태/마감/가격 검사 -> 갱신"을 한 번의 왕복으로 끝낸다.
-- 판정 순서는 Auction.assertBiddable / validateBid / applyBid와 똑같이 맞춰둔다.

local state = redis.call('HMGET', KEYS[1], 'price', 'unit', 'status', 'end_ms')
if not state[1] or not state[2] or not state[3] or not state[4] then
    return { 'MISS' }
end

local price = tonumber(state[1])
local unit = tonumber(state[2])
local status = state[3]
local endMs = tonumber(state[4])

local bid = tonumber(ARGV[1])
local nowMs = tonumber(ARGV[2])
local windowSec = tonumber(ARGV[3])
local extendSec = tonumber(ARGV[4])
local ttlSec = tonumber(ARGV[5])

-- Lua 숫자는 실수(double)라 그냥 tostring하면 큰 수가 1.7e+12처럼 지수 표기로 나올 수 있다.
-- 소수점 없는 정수 문자열로 바꿔서 돌려준다.
local function int(n)
    return string.format('%.0f', n)
end

local function reply(code, extended)
    return { code, int(price), int(unit), status, int(endMs), extended }
end

-- 1) 입찰 가능한 상태인가 (Auction.assertBiddable 첫 번째 검사)
if status ~= 'ONGOING' and status ~= 'EXTENDED' then
    return reply('NOT_BIDDABLE', '0')
end

-- 2) 마감 시각이 지났는가 (Auction.assertBiddable 두 번째 검사: now.isAfter(auctionEndAt))
if nowMs > endMs then
    return reply('ENDED', '0')
end

-- 3) 현재가 + 최소 단위 이상인가 (Auction.validateBid)
if bid < price + unit then
    return reply('TOO_LOW', '0')
end

-- 4) 이겼다. 마감 직전 입찰이면 연장한다 (Auction.applyBid와 같은 규칙)
--    Duration.between(now, end).getSeconds()는 소수점 아래를 버리므로 math.floor로 맞춘다.
local extended = '0'
if math.floor((endMs - nowMs) / 1000) <= windowSec then
    endMs = endMs + extendSec * 1000
    status = 'EXTENDED'
    extended = '1'
end
price = bid

redis.call('HSET', KEYS[1], 'price', int(price), 'status', status, 'end_ms', int(endMs))
-- 입찰이 계속 들어오는 경매는 키가 만료되지 않게 연장한다.
redis.call('EXPIRE', KEYS[1], ttlSec)

return reply('WIN', extended)
