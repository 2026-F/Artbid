-- 고정 윈도우 횟수 제한용 카운터. 증가와 만료시간 설정을 한 번에 처리한다.
-- INCR과 EXPIRE를 따로 보내면 그 사이에 서버가 죽었을 때 만료 없는 키가 남아 영영 제한에 걸린다.
-- KEYS[1] = 카운터 키, ARGV[1] = 윈도우 길이(ms). 반환값 = 증가 후 횟수.
local count = redis.call('INCR', KEYS[1])
-- 처음 만든 키에 만료를 건다. 예전 방식으로 만료 없이 남은 키도 여기서 만료를 다시 얻는다.
if count == 1 or redis.call('PTTL', KEYS[1]) == -1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return count
