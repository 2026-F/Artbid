package com.artbid.infra.streaming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ivs.IvsClient;
import software.amazon.awssdk.services.ivs.model.Channel;
import software.amazon.awssdk.services.ivs.model.ChannelLatencyMode;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.ChannelType;
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.DeleteChannelRequest;
import software.amazon.awssdk.services.ivs.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivs.model.StopStreamRequest;
import software.amazon.awssdk.services.ivs.model.StreamKey;

/**
 * AWS IVS Low-Latency 채널 생성/삭제, 스트림 키 발급을 담당.
 * 실제 RTMP 송출(OBS 등)과 시청자 HLS 재생 자체는 이 클라이언트의 책임 밖이며,
 * 여기서는 방송에 필요한 채널 리소스(ingest 엔드포인트, streamKey, playbackUrl)만 발급/정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IvsChannelClient {

    private final IvsClient ivsClient;

    /** 경매 하나당 IVS 채널을 하나 새로 만든다. */
    public IvsChannelInfo createChannel(Long auctionId) {
        CreateChannelResponse response = ivsClient.createChannel(CreateChannelRequest.builder()
                .name("artbid-auction-" + auctionId)
                .type(ChannelType.STANDARD)           // 최대 1080p, 저지연 모드 지원 (BASIC은 480p 한정)
                .latencyMode(ChannelLatencyMode.LOW)   // 실시간 경매 특성상 저지연 모드로 고정
                .authorized(false)                     // 재생 인증 없이 playbackUrl로 바로 시청 (P0 범위, 추후 필요 시 도입)
                .build());

        Channel channel = response.channel();
        StreamKey streamKey = response.streamKey();

        log.info("[IVS] 채널 생성 완료: auctionId={}, channelArn={}", auctionId, channel.arn());

        return new IvsChannelInfo(
                channel.arn(),
                streamKey.value(),
                channel.ingestEndpoint(),
                channel.playbackUrl()
        );
    }

    /** 방송 종료 처리 시 진행 중인 스트림을 강제 종료한다. 이미 방송 중이 아니면 조용히 무시한다. */
    public void stopStreamIfLive(String channelArn) {
        try {
            ivsClient.stopStream(StopStreamRequest.builder()
                    .channelArn(channelArn)
                    .build());
            log.info("[IVS] 스트림 강제 종료: channelArn={}", channelArn);
        } catch (ChannelNotBroadcastingException e) {
            log.debug("[IVS] 이미 방송 중이 아님(정상): channelArn={}", channelArn);
        }
    }

    /**
     * 경매가 완전히 종료되어 다시 쓸 일이 없는 채널을 삭제해 리소스를 정리한다.
     * 이미 삭제된 채널(예: 종료 API 중복 호출)이면 에러 없이 조용히 무시한다.
     */
    public void deleteChannelIfExists(String channelArn) {
        try {
            ivsClient.deleteChannel(DeleteChannelRequest.builder()
                    .arn(channelArn)
                    .build());
            log.info("[IVS] 채널 삭제 완료: channelArn={}", channelArn);
        } catch (ResourceNotFoundException e) {
            log.debug("[IVS] 이미 삭제된 채널(정상): channelArn={}", channelArn);
        }
    }

    /**
     * 채널 생성 결과.
     * ingestEndpoint + streamKey를 합치면 위탁자가 OBS 등에 등록할 RTMP(S) 주소가 되고
     * (예: rtmps://{ingestEndpoint}:443/app/{streamKey}), playbackUrl은 시청자가 재생(HLS)할 주소다.
     */
    public record IvsChannelInfo(String channelArn, String streamKey, String ingestEndpoint, String playbackUrl) {
    }
}
