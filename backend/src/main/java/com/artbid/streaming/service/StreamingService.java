package com.artbid.streaming.service;

import com.artbid.infra.streaming.IvsChannelClient;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.domain.LivestreamStatus;
import com.artbid.streaming.repository.LivestreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StreamingService {

	private final LivestreamRepository livestreamRepository;
	private final IvsChannelClient ivsChannelClient;

	/**
	 * 경매의 AWS IVS 채널을 새로 만들고 스트림 키/ingest 주소/재생 URL을 저장한다.
	 * 이미 LIVE 상태 스트림이 있으면 막고, 한 번 끝난(ENDED) 적 있는 경매거나 처음 시작하는
	 * 경매면 (기존 행을 재사용하거나 새로 만들어) 새 채널을 발급한다.
	 */
	@Transactional
	public Livestream startStream(Long auctionId) {
		Livestream livestream = livestreamRepository.findByAuctionId(auctionId)
				.orElseGet(() -> Livestream.builder().auctionId(auctionId).build());

		if (livestream.getStatus() == LivestreamStatus.LIVE) {
			throw new IllegalStateException("이미 진행 중인 스트림이 있습니다: auctionId=" + auctionId);
		}

		IvsChannelClient.IvsChannelInfo channelInfo = ivsChannelClient.createChannel(auctionId);
		livestream.activate(
				channelInfo.channelArn(),
				channelInfo.streamKey(),
				channelInfo.ingestEndpoint(),
				channelInfo.playbackUrl());

		return livestreamRepository.save(livestream);
	}

	/**
	 * 방송 종료: 진행 중이면 IVS 스트림을 강제 종료하고, 다시 쓸 일 없는 채널은 삭제한 뒤
	 * 상태를 ENDED로 반영한다. 이미 삭제된 채널에 다시 호출돼도(중복 호출) 에러 없이 무시한다.
	 */
	@Transactional
	public void endStream(Long auctionId) {
		Livestream livestream = getStream(auctionId);

		ivsChannelClient.stopStreamIfLive(livestream.getIvsChannelArn());
		ivsChannelClient.deleteChannelIfExists(livestream.getIvsChannelArn());
		livestream.markEnded();
	}

	public Livestream getStream(Long auctionId) {
		return livestreamRepository.findByAuctionId(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("진행 중인 스트림이 없습니다."));
	}
}
