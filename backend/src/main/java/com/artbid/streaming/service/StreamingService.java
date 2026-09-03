package com.artbid.streaming.service;

import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.repository.LivestreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StreamingService {

	private final LivestreamRepository livestreamRepository;

	// TODO: infra/streaming의 IvsChannelClient로 채널 생성 후 streamKey/playbackUrl 저장
	public Livestream startStream(Long auctionId) {
		throw new UnsupportedOperationException("TODO: AWS IVS 채널 생성 연동 구현");
	}

	// TODO: 채널 상태를 ENDED로 전환, 필요 시 IVS 채널 정리
	public void endStream(Long auctionId) {
		throw new UnsupportedOperationException("TODO: 스트림 종료 처리 구현");
	}

	public Livestream getStream(Long auctionId) {
		return livestreamRepository.findByAuctionId(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("진행 중인 스트림이 없습니다."));
	}
}
