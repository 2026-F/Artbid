package com.artbid.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class PaymentClockConfiguration {

	@Bean
	public Clock paymentClock() {
		// 정산 기한은 배포 Compose에서 사용하는 한국 시간의 LocalDateTime으로 저장한다.
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
