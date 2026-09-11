package com.artbid.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * application.yml 의 app.* 값을 바인딩한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 비어있으면 요청 Host 기준으로 자동 생성, 값이 있으면 이 값을 QR/링크의 base 로 사용 */
    private String publicBaseUrl = "";

    /** 데모 경매 아이템 초기값 (item id -> 설정) */
    private Map<String, DemoItem> demoItems = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class DemoItem {
        private String title;
        private String youtubeVideoId;
        private long initialPrice;
    }
}
