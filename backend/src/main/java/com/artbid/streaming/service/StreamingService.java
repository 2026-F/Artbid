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
	 * 경매 하나당 AWS IVS 채널을 새로 만들고 스트림 키/ingest 주소/재생 URL을 저장한다.
	 * 한 경매 = 한 스트림으로 두고, 이미 채널이 만들어진 경매면 새로 만들지 않고 예외를 던진다.
	 */
	@Transactional
	public Livestream startStream(Long auctionId) {
		livestreamRepository.findByAuctionId(auctionId).ifPresent(existing -> {
			throw new IllegalStateException(
					"이미 스트림이 생성된 경매입니다: auctionId=" + auctionId + ", status=" + existing.getStatus());
		});

		IvsChannelClient.IvsChannelInfo channelInfo = ivsChannelClient.createChannel(auctionId);

		Livestream livestream = Livestream.builder()
				.auctionId(auctionId)
				.ivsChannelArn(channelInfo.channelArn())
				.streamKey(channelInfo.streamKey())
				.ingestEndpoint(channelInfo.ingestEndpoint())
				.playbackUrl(channelInfo.playbackUrl())
				.status(LivestreamStatus.LIVE)
				.build();

		return livestreamRepository.save(livestream);
	}

	/**
	 * 방송 종료: 진행 중이면 IVS 스트림을 강제 종료하고, 다시 쓸 일 없는 채널은 삭제한 뒤
	 * 상태를 ENDED로 반영한다.
	 */
	@Transactional
	public void endStream(Long auctionId) {
		Livestream livestream = getStream(auctionId);

		ivsChannelClient.stopStreamIfLive(livestream.getIvsChannelArn());
		ivsChannelClient.deleteChannel(livestream.getIvsChannelArn());
		livestream.markEnded();
	}

	public Livestream getStream(Long auctionId) {
		return livestreamRepository.findByAuctionId(auctionId)
				.orElseThrow(() -> new IllegalArgumentException("진행 중인 스트림이 없습니다."));
	}
}
