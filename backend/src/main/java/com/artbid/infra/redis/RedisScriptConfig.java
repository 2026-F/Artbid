package com.artbid.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

// Lua 스크립트는 앱 시작할 때 한 번만 읽어서 빈으로 등록해둔다 (요청마다 파일을 읽지 않는다).
// 실행할 때는 Spring이 EVALSHA(스크립트 해시)로 먼저 보내고, Redis에 아직 없으면 EVAL로 본문을 보낸다.
@Configuration
public class RedisScriptConfig {

	// 입찰 판정: 상태/마감/가격 검사와 현재가·마감 갱신을 한 번에 한다. 결과는 문자열 리스트.
	@Bean
	@SuppressWarnings("rawtypes")
	public RedisScript<List> bidScript() {
		return RedisScript.of(new ClassPathResource("scripts/bid.lua"), List.class);
	}

	// 캐시 미스 때 DB 값으로 입찰 판정용 해시를 채운다. 이미 있으면 덮어쓰지 않는다.
	@Bean
	public RedisScript<Long> primeBidStateScript() {
		return RedisScript.of(new ClassPathResource("scripts/prime_bid_state.lua"), Long.class);
	}
}
