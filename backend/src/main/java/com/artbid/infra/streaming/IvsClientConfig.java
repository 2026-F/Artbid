package com.artbid.infra.streaming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ivs.IvsClient;
import software.amazon.awssdk.services.ivsrealtime.IvsRealTimeClient;

/**
 * AWS IVS 연동에 쓰는 클라이언트 빈 설정.
 * 자격 증명은 코드/설정 파일에 절대 넣지 않고, AWS SDK 기본 자격 증명 체인
 * (환경 변수, ~/.aws/credentials, IAM 역할 등)을 그대로 사용한다.
 */
@Configuration
public class IvsClientConfig {

    @Value("${app.aws.region:ap-northeast-2}")
    private String region;

    @Bean
    public IvsClient ivsClient() {
        return IvsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /** Real-Time Streaming(스테이지·참여 토큰) 전용 클라이언트. 저지연 채널용 IvsClient와 API가 분리되어 있다. */
    @Bean
    public IvsRealTimeClient ivsRealTimeClient() {
        return IvsRealTimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
