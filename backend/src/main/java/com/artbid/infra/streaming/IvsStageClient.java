package com.artbid.infra.streaming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ivsrealtime.IvsRealTimeClient;
import software.amazon.awssdk.services.ivsrealtime.model.CreateParticipantTokenRequest;
import software.amazon.awssdk.services.ivsrealtime.model.CreateStageRequest;
import software.amazon.awssdk.services.ivsrealtime.model.DeleteStageRequest;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantToken;
import software.amazon.awssdk.services.ivsrealtime.model.ParticipantTokenCapability;
import software.amazon.awssdk.services.ivsrealtime.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivsrealtime.model.Stage;

import java.time.Duration;
import java.util.Map;

/**
 * AWS IVS Real-Time Streaming 스테이지 생성/삭제와 참여 토큰 발급을 담당.
 * 스테이지는 WebRTC 기반 "방"이다. 위탁자는 PUBLISH 토큰으로 영상을 올리고, 시청자는
 * SUBSCRIBE 토큰으로 영상을 받는다.
 * 저지연 채널과 달리 RTMP 주소·streamKey·playbackUrl 같은 값은 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IvsStageClient {

    private final IvsRealTimeClient ivsRealTimeClient;

    /** 경매 하나당 스테이지를 하나 새로 만들고 ARN을 돌려준다. */
    public String createStage(Long auctionId) {
        Stage stage = ivsRealTimeClient.createStage(CreateStageRequest.builder()
                        .name(stageName(auctionId))
                        // AWS 콘솔에서 어떤 경매의 스테이지인지 찾기 쉽게 태그를 붙여 둔다.
                        .tags(Map.of("service", "artbid", "auctionId", String.valueOf(auctionId)))
                        .build())
                .stage();

        log.info("[IVS] 스테이지 생성 완료: auctionId={}, stageArn={}", auctionId, stage.arn());
        return stage.arn();
    }

    /**
     * 방송이 끝난 스테이지를 삭제한다. 삭제하면 접속 중인 참여자는 모두 연결이 끊긴다.
     * 이미 삭제된 스테이지(예: 종료 API 중복 호출)면 에러 없이 조용히 무시한다.
     */
    public void deleteStageIfExists(String stageArn) {
        try {
            ivsRealTimeClient.deleteStage(DeleteStageRequest.builder()
                    .arn(stageArn)
                    .build());
            log.info("[IVS] 스테이지 삭제 완료: stageArn={}", stageArn);
        } catch (ResourceNotFoundException e) {
            log.debug("[IVS] 이미 삭제된 스테이지(정상): stageArn={}", stageArn);
        }
    }

    /** 스테이지 이름은 영문·숫자·-·_ 만 허용되고 128자 이하여야 한다. */
    static String stageName(Long auctionId) {
        return "artbid-auction-" + auctionId;
    }

    /**
     * 스테이지에 들어갈 참여 토큰을 발급한다.
     * duration은 분 단위로만 받을 수 있고(AWS 쪽 최댓값 180분), 초 단위 이하는 버림된다.
     */
    public ParticipantToken createParticipantToken(String stageArn, String userId,
            ParticipantTokenCapability capability, Duration duration) {
        ParticipantToken token = ivsRealTimeClient.createParticipantToken(CreateParticipantTokenRequest.builder()
                        .stageArn(stageArn)
                        .userId(userId)
                        .capabilities(capability)
                        .duration((int) duration.toMinutes())
                        .build())
                .participantToken();

        log.info("[IVS] 참여 토큰 발급: stageArn={}, userId={}, capability={}", stageArn, userId, capability);
        return token;
    }
}
