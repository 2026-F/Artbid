package com.artbid.infra.streaming;

import org.springframework.stereotype.Component;

@Component
public class IvsChannelClient {
	// TODO: AWS IVS 채널 생성/조회, streamKey 발급, RTMP ingest 엔드포인트 반환
	public String createChannel(Long auctionId) {
		throw new UnsupportedOperationException("TODO: AWS IVS 채널 생성 연동 구현");
	}
}
