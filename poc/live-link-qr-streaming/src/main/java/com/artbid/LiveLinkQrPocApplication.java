package com.artbid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 외부 라이브 연동 검증(고정 링크/QR 진입 + 실시간 호가 반영) PoC 전용 애플리케이션.
 * backend 모듈과는 별도로 실행되는 독립 스프링부트 앱입니다.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class LiveLinkQrPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveLinkQrPocApplication.class, args);
    }
}
