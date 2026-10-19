package com.artbid.streaming.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.streaming.config.StreamReconnectProperties;
import com.artbid.streaming.domain.Livestream;
import com.artbid.streaming.domain.LivestreamStatus;
import com.artbid.streaming.exception.LivestreamAlreadyLiveException;
import com.artbid.streaming.exception.LivestreamNotFoundException;
import com.artbid.streaming.exception.StreamingAccessDeniedException;
import com.artbid.streaming.repository.LivestreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StreamingService {

	private final LivestreamRepository livestreamRepository;
	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;
	private final IvsStageClient ivsStageClient;
	private final StreamReconnectProperties reconnectProperties;

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

	/**
	 * 위탁자의 연결이 끊겼음을 본인이 직접 알려온다(IVS 스테이지의 연결 상태 이벤트를 보고 클라이언트가 호출).
	 * 스테이지는 삭제하지 않는다 — 재연결 시 같은 스테이지로 이어 붙어야 하기 때문이다.
	 */
	@Transactional
	public Livestream reportDisconnected(Long auctionId, Long requesterId) {
		Livestream livestream = getStream(auctionId);
		requireConsignor(auctionId, requesterId);
		livestream.markDisconnected(LocalDateTime.now());
		return livestream;
	}

	/** 끊겼던 위탁자가 같은 스테이지로 다시 들어왔음을 알려온다. */
	@Transactional
	public Livestream reconnect(Long auctionId, Long requesterId) {
		Livestream livestream = getStream(auctionId);
		requireConsignor(auctionId, requesterId);
		livestream.reconnect();
		return livestream;
	}

	/**
	 * 끊긴 채로 timeout을 넘겨 방치된 방송들을 찾아 스테이지를 삭제하고 종료 처리한다.
	 * DisconnectedStreamSweeper가 주기적으로 호출한다.
	 */
	@Transactional
	public void endAbandonedDisconnectedStreams() {
		LocalDateTime cutoff = LocalDateTime.now().minus(reconnectProperties.getTimeout());
		for (Livestream livestream : livestreamRepository.findAllByStatus(LivestreamStatus.DISCONNECTED)) {
			if (livestream.getDisconnectedAt() != null && livestream.getDisconnectedAt().isBefore(cutoff)) {
				ivsStageClient.deleteStageIfExists(livestream.getStageArn());
				livestream.markEnded(LocalDateTime.now());
			}
		}
	}

	/** 이 경매의 위탁 작품을 올린 본인인지 확인한다. 아니면(비로그인 포함) 403으로 막는다. */
	private void requireConsignor(Long auctionId, Long requesterId) {
		boolean isConsignor = requesterId != null && auctionRepository.findById(auctionId)
				.map(Auction::getArtworkId)
				.flatMap(artworkRepository::findById)
				.map(Artwork::getConsignorId)
				.map(requesterId::equals)
				.orElse(false);
		if (!isConsignor) {
			throw new StreamingAccessDeniedException(auctionId);
		}
	}
}
