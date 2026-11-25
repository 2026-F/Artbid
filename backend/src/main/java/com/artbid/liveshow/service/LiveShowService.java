package com.artbid.liveshow.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.domain.AuctionStatus;
import com.artbid.auction.live.AuctionLiveChangedEvent;
import com.artbid.auction.live.AuctionLiveReason;
import com.artbid.auction.live.AuctionLiveState;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.infra.streaming.IvsStageClient;
import com.artbid.liveshow.domain.LiveShow;
import com.artbid.liveshow.domain.LiveShowLot;
import com.artbid.liveshow.domain.LiveShowStatus;
import com.artbid.liveshow.domain.LotStatus;
import com.artbid.liveshow.dto.LiveShowDtos.CreateRequest;
import com.artbid.liveshow.dto.LiveShowDtos.DetailResponse;
import com.artbid.liveshow.dto.LiveShowDtos.LotRequest;
import com.artbid.liveshow.dto.LiveShowDtos.LotResponse;
import com.artbid.liveshow.dto.LiveShowDtos.SummaryResponse;
import com.artbid.liveshow.exception.LiveShowAccessDeniedException;
import com.artbid.liveshow.exception.LiveShowConflictException;
import com.artbid.liveshow.exception.LiveShowNotFoundException;
import com.artbid.liveshow.repository.LiveShowLotRepository;
import com.artbid.liveshow.repository.LiveShowRepository;
import com.artbid.member.domain.Member;
import com.artbid.member.domain.MemberRole;
import com.artbid.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 여러 작품을 차례로 진행하는 라이브 편성.
 * - 편성: 위탁자가 자기 작품의 시작 전 경매를 골라 순서와 작품별 진행 시간을 정한다.
 * - 진행: 진행자가 다음 작품을 열면 그 경매가 지금부터 진행 시간 동안 입찰을 받는다. 미리 입찰은 없다.
 * - 마감: 기존 경매 규칙(마감 30초 이내 입찰 시 30초 연장, 스케줄러 마감)을 그대로 쓴다.
 * 권한은 요청 본문이 아니라 서버가 검증한 회원 ID와 역할로만 판단한다.
 */
@Service
@RequiredArgsConstructor
public class LiveShowService {

	static final int MAX_LOTS = 50;

	private final LiveShowRepository showRepository;
	private final LiveShowLotRepository lotRepository;
	private final AuctionRepository auctionRepository;
	private final ArtworkRepository artworkRepository;
	private final MemberRepository memberRepository;
	private final IvsStageClient ivsStageClient;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public DetailResponse create(Long hostId, MemberRole role, CreateRequest request) {
		if (role != MemberRole.CONSIGNOR) {
			throw new LiveShowAccessDeniedException("위탁자만 라이브를 편성할 수 있습니다.");
		}
		if (request == null || request.lots() == null || request.lots().isEmpty()) {
			throw new IllegalArgumentException("라이브에 넣을 작품을 1개 이상 골라주세요.");
		}
		if (request.lots().size() > MAX_LOTS) {
			throw new IllegalArgumentException("한 라이브에는 작품을 " + MAX_LOTS + "개까지 넣을 수 있습니다.");
		}

		List<Long> auctionIds = request.lots().stream().map(LotRequest::auctionId).toList();
		if (auctionIds.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("경매 ID는 필수입니다.");
		}
		if (new HashSet<>(auctionIds).size() != auctionIds.size()) {
			throw new IllegalArgumentException("같은 경매를 두 번 넣을 수 없습니다.");
		}

		Map<Long, Auction> auctions = auctionRepository.findAllById(auctionIds).stream()
				.collect(Collectors.toMap(Auction::getId, Function.identity()));
		Map<Long, Artwork> artworks = artworksOf(auctions.values());
		for (Long auctionId : auctionIds) {
			Auction auction = auctions.get(auctionId);
			if (auction == null) {
				throw new IllegalArgumentException("경매를 찾을 수 없습니다: " + auctionId);
			}
			Artwork artwork = artworks.get(auction.getArtworkId());
			if (artwork == null || !hostId.equals(artwork.getConsignorId())) {
				throw new LiveShowAccessDeniedException("본인이 위탁한 작품의 경매만 편성할 수 있습니다: " + auctionId);
			}
			if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.PREVIEW) {
				throw new LiveShowConflictException(
						"시작 전 경매만 편성할 수 있습니다. 경매 " + auctionId + " 상태: " + auction.getStatus());
			}
		}
		if (lotRepository.existsByAuctionIdIn(auctionIds)) {
			throw new LiveShowConflictException("이미 다른 라이브에 들어 있는 경매가 있습니다.");
		}

		LocalDateTime now = LocalDateTime.now();
		LiveShow show = showRepository.save(
				LiveShow.create(hostId, request.title(), request.description(), request.scheduledAt(), now));

		List<LiveShowLot> lots = new ArrayList<>();
		for (int i = 0; i < request.lots().size(); i++) {
			LotRequest lot = request.lots().get(i);
			lots.add(LiveShowLot.of(show.getId(), lot.auctionId(), i + 1, lot.durationMinutes()));
		}
		try {
			// 동시에 같은 경매를 두 라이브에 넣으면 auction_id 유일 제약에서 걸린다.
			lotRepository.saveAllAndFlush(lots);
		} catch (DataIntegrityViolationException e) {
			throw new LiveShowConflictException("이미 다른 라이브에 들어 있는 경매가 있습니다.");
		}
		return toDetail(show, lots);
	}

	@Transactional(readOnly = true)
	public DetailResponse getDetail(Long showId) {
		LiveShow show = showRepository.findById(showId).orElseThrow(() -> new LiveShowNotFoundException(showId));
		return toDetail(show, lotRepository.findByShowIdOrderByLotNo(showId));
	}

	/** 상태별 라이브 목록. 최근에 만든 라이브가 먼저 온다. */
	@Transactional(readOnly = true)
	public List<SummaryResponse> list(LiveShowStatus status) {
		List<LiveShow> shows = showRepository.findByStatusOrderByIdDesc(status == null ? LiveShowStatus.LIVE : status);
		if (shows.isEmpty()) {
			return List.of();
		}
		Map<Long, List<LiveShowLot>> lotsByShow = lotRepository.findByShowIdIn(shows.stream().map(LiveShow::getId).toList())
				.stream().collect(Collectors.groupingBy(LiveShowLot::getShowId));

		List<LiveShowLot> currentLots = shows.stream()
				.map(show -> currentLot(show, lotsByShow.getOrDefault(show.getId(), List.of())))
				.filter(Objects::nonNull)
				.toList();
		LotLookup lookup = lookup(currentLots);
		Map<Long, String> nicknames = nicknames(shows.stream().map(LiveShow::getHostId).toList());

		return shows.stream().map(show -> {
			List<LiveShowLot> lots = lotsByShow.getOrDefault(show.getId(), List.of());
			LiveShowLot current = currentLot(show, lots);
			return new SummaryResponse(show.getId(), show.getHostId(), nicknames.get(show.getHostId()), show.getTitle(),
					show.getStatus(), show.getScheduledAt(), show.getStartedAt(), lots.size(),
					current == null ? null : lookup.toResponse(current));
		}).toList();
	}

	/** 진행자가 방송을 시작한다. IVS 스테이지를 만든다. 작품은 따로 열어야 입찰이 시작된다. */
	@Transactional
	public DetailResponse start(Long showId, Long memberId) {
		LiveShow show = lockAsHost(showId, memberId);
		if (show.getStatus() != LiveShowStatus.SCHEDULED) {
			throw new LiveShowConflictException("시작 전 라이브만 시작할 수 있습니다. 현재 상태: " + show.getStatus());
		}
		show.start(ivsStageClient.createShowStage(showId), LocalDateTime.now());
		return toDetail(show, lotRepository.findByShowIdOrderByLotNo(showId));
	}

	/**
	 * 진행자가 다음 작품을 연다. 현재 작품의 경매가 마감된 뒤에만 넘길 수 있다(진행자가 마감을 앞당기지 않는다).
	 * 연 경매는 지금부터 작품별 진행 시간 동안 입찰을 받는다.
	 */
	@Transactional
	public DetailResponse openNextLot(Long showId, Long memberId) {
		LiveShow show = lockAsHost(showId, memberId);
		if (!show.isLive()) {
			throw new LiveShowConflictException("진행 중인 라이브에서만 작품을 열 수 있습니다. 현재 상태: " + show.getStatus());
		}
		List<LiveShowLot> lots = lotRepository.findByShowIdOrderByLotNo(showId);

		for (LiveShowLot open : lots) {
			if (open.getStatus() != LotStatus.OPEN) {
				continue;
			}
			Auction current = auctionRepository.findById(open.getAuctionId())
					.orElseThrow(() -> new IllegalStateException("경매를 찾을 수 없습니다: " + open.getAuctionId()));
			if (current.getStatus() != AuctionStatus.CLOSED) {
				throw new LiveShowConflictException("현재 작품의 입찰이 아직 진행 중입니다. 마감된 뒤에 다음 작품을 열 수 있습니다.");
			}
			open.finish();
		}

		LiveShowLot next = lots.stream()
				.filter(lot -> lot.getStatus() == LotStatus.WAITING)
				.findFirst()
				.orElseThrow(() -> new LiveShowConflictException("더 진행할 작품이 없습니다."));

		Auction auction = auctionRepository.findByIdForUpdate(next.getAuctionId())
				.orElseThrow(() -> new IllegalStateException("경매를 찾을 수 없습니다: " + next.getAuctionId()));
		if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.PREVIEW) {
			throw new LiveShowConflictException(
					"시작 전 경매만 열 수 있습니다. 경매 " + auction.getId() + " 상태: " + auction.getStatus());
		}

		LocalDateTime now = LocalDateTime.now();
		auction.openForLive(now, next.getDurationMinutes());
		artworkRepository.findById(auction.getArtworkId()).ifPresent(Artwork::markInAuction);
		next.open(now);
		show.moveTo(next.getId());

		// 입찰 판정 캐시를 비우고(AuctionBidCacheInvalidator) 경매 화면에 새 상태를 알린다. 커밋 후에 처리된다.
		eventPublisher.publishEvent(new AuctionLiveChangedEvent(
				AuctionLiveState.changed(auction, now), AuctionLiveReason.SNAPSHOT, false));
		return toDetail(show, lots);
	}

	/**
	 * 진행자가 방송을 끝낸다. 스테이지를 지우고, 열지 않은 작품은 편성에서 빼서 다른 라이브에 다시 넣을 수 있게 한다.
	 * 입찰 중인 작품이 있으면 그 경매는 원래 마감 시각까지 계속 입찰을 받는다.
	 */
	@Transactional
	public DetailResponse end(Long showId, Long memberId) {
		LiveShow show = lockAsHost(showId, memberId);
		if (!show.isLive()) {
			throw new LiveShowConflictException("진행 중인 라이브만 종료할 수 있습니다. 현재 상태: " + show.getStatus());
		}
		ivsStageClient.deleteStageIfExists(show.getStageArn());
		show.end(LocalDateTime.now());

		List<LiveShowLot> lots = lotRepository.findByShowIdOrderByLotNo(showId);
		List<LiveShowLot> waiting = lots.stream().filter(lot -> lot.getStatus() == LotStatus.WAITING).toList();
		lotRepository.deleteAll(waiting);

		List<LiveShowLot> remaining = lots.stream().filter(lot -> lot.getStatus() != LotStatus.WAITING).toList();
		return toDetail(show, remaining);
	}

	private LiveShow lockAsHost(Long showId, Long memberId) {
		LiveShow show = showRepository.findByIdForUpdate(showId).orElseThrow(() -> new LiveShowNotFoundException(showId));
		if (!show.isHost(memberId)) {
			throw new LiveShowAccessDeniedException("이 라이브의 진행자만 할 수 있습니다.");
		}
		return show;
	}

	private LiveShowLot currentLot(LiveShow show, List<LiveShowLot> lots) {
		if (show.getCurrentLotId() == null) {
			return null;
		}
		return lots.stream().filter(lot -> lot.getId().equals(show.getCurrentLotId())).findFirst().orElse(null);
	}

	private DetailResponse toDetail(LiveShow show, List<LiveShowLot> lots) {
		LotLookup lookup = lookup(lots);
		List<LotResponse> lotResponses = lots.stream()
				.sorted(Comparator.comparingInt(LiveShowLot::getLotNo))
				.map(lookup::toResponse)
				.toList();
		String nickname = nicknames(List.of(show.getHostId())).get(show.getHostId());
		return new DetailResponse(show.getId(), show.getHostId(), nickname, show.getTitle(), show.getDescription(),
				show.getStatus(), show.getScheduledAt(), show.getStartedAt(), show.getEndedAt(), show.getCurrentLotId(),
				lotResponses);
	}

	private LotLookup lookup(Collection<LiveShowLot> lots) {
		Map<Long, Auction> auctions = auctionRepository.findAllById(lots.stream().map(LiveShowLot::getAuctionId).toList())
				.stream().collect(Collectors.toMap(Auction::getId, Function.identity()));
		return new LotLookup(auctions, artworksOf(auctions.values()));
	}

	private Map<Long, Artwork> artworksOf(Collection<Auction> auctions) {
		List<Long> artworkIds = auctions.stream().map(Auction::getArtworkId).filter(Objects::nonNull).distinct().toList();
		return artworkRepository.findAllById(artworkIds).stream()
				.collect(Collectors.toMap(Artwork::getId, Function.identity()));
	}

	private Map<Long, String> nicknames(Collection<Long> memberIds) {
		Map<Long, String> result = new HashMap<>();
		for (Member member : memberRepository.findAllById(new HashSet<>(memberIds))) {
			result.put(member.getId(), member.getNickname());
		}
		return result;
	}

	private record LotLookup(Map<Long, Auction> auctions, Map<Long, Artwork> artworks) {

		LotResponse toResponse(LiveShowLot lot) {
			Auction auction = auctions.get(lot.getAuctionId());
			Artwork artwork = auction == null ? null : artworks.get(auction.getArtworkId());
			// 경매가 마감되면 진행자가 다음 작품을 열기 전이라도 마감으로 보여준다.
			LotStatus status = lot.getStatus() == LotStatus.OPEN && auction != null
					&& auction.getStatus() == AuctionStatus.CLOSED ? LotStatus.DONE : lot.getStatus();
			return new LotResponse(
					lot.getId(), lot.getLotNo(), status, lot.getDurationMinutes(), lot.getOpenedAt(),
					lot.getAuctionId(),
					auction == null ? null : auction.getStatus(),
					auction == null ? null : auction.getCurrentPrice(),
					auction == null ? null : auction.getMinBidUnit(),
					auction == null ? null : auction.getAuctionEndAt(),
					artwork == null ? null : artwork.getId(),
					artwork == null ? null : artwork.getTitle(),
					artwork == null ? null : artwork.getImageUrl());
		}
	}
}
