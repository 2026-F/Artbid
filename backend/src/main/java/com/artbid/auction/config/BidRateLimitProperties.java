package com.artbid.auction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 입찰 요청 횟수 제한 설정. 회원 한 명이 매크로로 입찰을 연타하지 못하게 한다.
 * nginx의 IP 기준 제한은 기숙사, 학교처럼 IP를 공유하는 사용자 때문에 느슨하게 두었다.
 * 매크로 차단은 여기서 회원 기준으로 한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.bid")
public class BidRateLimitProperties {

	/** 끄면 횟수를 세지 않는다. 부하테스트에서 제한 없는 처리량을 볼 때만 끈다. */
	private boolean rateLimitEnabled = true;

	/** 회원별 입찰 제한: 이 기간 동안 허용하는 최대 입찰(제출, 취소 합산) 수 */
	private int rateLimit = 5;

	private Duration rateLimitWindow = Duration.ofSeconds(1);
}
