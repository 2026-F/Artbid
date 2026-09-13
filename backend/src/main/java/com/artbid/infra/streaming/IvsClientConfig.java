package com.artbid.infra.streaming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ivs.IvsClient;

/**
 * AWS IVS 채널 생성/삭제에 쓰는 IvsClient 빈 설정.
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
}
