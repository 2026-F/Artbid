package com.artbid.streaming.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** QR코드 등에서 프론트엔드 페이지로 바로 연결할 때 쓰는 기본 URL. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.frontend")
public class FrontendProperties {

	private String baseUrl = "http://localhost:3000";
}
