package com.artbid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfig {

	@Bean
	public RedisScript<Long> updateCurrentPriceScript() {
		// resources/scripts/update_current_price.lua 파일을 읽어서 등록해둔다.
		// 매번 파일을 읽는 게 아니라, 앱 시작할 때 한 번만 로드된다.
		return RedisScript.of(new ClassPathResource("scripts/update_current_price.lua"), Long.class);
	}
}