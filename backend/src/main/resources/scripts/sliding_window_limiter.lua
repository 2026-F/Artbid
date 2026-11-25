-- 슬라이딩 윈도우 로그 방식 횟수 제한. Sorted Set에 요청 시각(ms)을 score로 쌓고,
-- 윈도우 밖 기록을 지운 뒤 남은 개수로 판정한다.
-- 정리, 세기, 추가를 한 스크립트로 처리해야 동시 요청 두 개가 같은 개수를 보고 둘 다 통과하는 일이 없다.
-- 시각은 앱 서버가 아니라 Redis의 TIME을 쓴다. 인스턴스마다 시계가 조금 달라도 같은 기준으로 센다.
-- KEYS[1] = 기록 키, ARGV[1] = 윈도우 길이(ms), ARGV[2] = 허용 횟수, ARGV[3] = 이번 요청의 고유 ID
-- 반환값 = {허용 여부(1/0), 윈도우 안 요청 수, 다시 시도까지 남은 ms}
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
local count = redis.call('ZCARD', KEYS[1])

if count < limit then
  -- member를 시각으로 쓰면 같은 ms에 들어온 요청이 하나로 합쳐진다. 그래서 요청마다 고유 ID를 쓴다.
  redis.call('ZADD', KEYS[1], now, ARGV[3])
  -- 요청이 끊기면 윈도우가 지난 뒤 키 자체가 사라진다.
  redis.call('PEXPIRE', KEYS[1], window)
  return {1, count + 1, 0}
end

-- 거절된 요청은 기록하지 않는다. 연타해도 차단 시간이 계속 늘어나지 않는다.
-- 가장 오래된 기록이 윈도우 밖으로 나가는 시점이 되면 한 건을 더 보낼 수 있다.
local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
local retryAfter = window
if oldest[2] then
  retryAfter = math.max(0, tonumber(oldest[2]) + window - now)
end
return {0, count, retryAfter}
