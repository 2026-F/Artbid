package com.artbid.streaming.service;

import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.exception.LivestreamAlreadyLiveException;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import com.artbid.streaming.repository.LivestreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StreamingService {

	private final LivestreamRepository livestreamRepository;
	private final IvsStageClient ivsStageClient;

	/**
	 * 경매의 IVS 스테이지를 새로 만들고 ARN을 저장한다.
	 * 이미 LIVE 상태면 막고, 한 번 끝난(ENDED) 적 있는 경매거나 처음 시작하는 경매면
	 * (기존 행을 재사용하거나 새로 만들어) 새 스테이지를 발급한다.
	 */
	@Transactional
	public Livestream startStream(Long auctionId) {
		Livestream livestream = livestreamRepository.findByAuctionId(auctionId)
				.orElseGet(() -> Livestream.builder().auctionId(auctionId).build());

		if (livestream.isLive()) {
			throw new LivestreamAlreadyLiveException(auctionId);
		}

		String stageArn = ivsStageClient.createStage(auctionId);
		livestream.activate(stageArn, LocalDateTime.now());

		return livestreamRepository.save(livestream);
	}

	/**
	 * 방송 종료: 스테이지를 삭제해 참여자 연결을 모두 끊고 상태를 ENDED로 바꾼다.
	 * 이미 끝난 방송에 다시 호출되면 아무것도 하지 않는다(중복 호출에도 안전).
	 */
	@Transactional
	public void endStream(Long auctionId) {
		Livestream livestream = getStream(auctionId);
		if (!livestream.isLive()) {
			return;
		}

		ivsStageClient.deleteStageIfExists(livestream.getStageArn());
		livestream.markEnded(LocalDateTime.now());
	}

	@Transactional(readOnly = true)
	public Livestream getStream(Long auctionId) {
		return livestreamRepository.findByAuctionId(auctionId)
				.orElseThrow(() -> new LivestreamNotFoundException(auctionId));
	}
}
