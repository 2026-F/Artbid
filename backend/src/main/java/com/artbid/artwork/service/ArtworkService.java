package com.artbid.artwork.service;

import com.artbid.artist.domain.Artist;
import com.artbid.artist.exception.ArtistNotFoundException;
import com.artbid.artist.repository.ArtistRepository;
import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.dto.ArtworkCreateRequest;
import com.artbid.artwork.dto.ArtworkDetailResponse;
import com.artbid.artwork.dto.ArtworkPageResponse;
import com.artbid.artwork.dto.ArtworkUpdateRequest;
import com.artbid.artwork.exception.ArtworkNotFoundException;
import com.artbid.artwork.repository.ArtworkRepository;
import com.artbid.artwork.repository.ArtworkSearchCondition;
import com.artbid.artwork.repository.ArtworkSpecifications;
import com.artbid.auction.domain.Auction;
import com.artbid.media.repository.ArtworkMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artbid.auction.repository.AuctionRepository;


import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtworkService {

	private final ArtworkRepository artworkRepository;
	private final ArtistRepository artistRepository;
	private final ArtworkMediaRepository artworkMediaRepository;
	private final AuctionRepository auctionRepository;


	@Transactional
	public ArtworkDetailResponse register(ArtworkCreateRequest request) {
		requireArtistExists(request.artistId());

		Artwork artwork = Artwork.builder()
				.consignorId(request.consignorId())
				.artistId(request.artistId())
				.title(request.title())
				.description(request.description())
				.category(request.category())
				.medium(request.medium())
				.widthCm(request.widthCm())
				.heightCm(request.heightCm())
				.depthCm(request.depthCm())
				.productionYear(request.productionYear())
				.startPrice(request.startPrice())
				.estimatedPrice(request.estimatedPrice())
				.certificateUrl(request.certificateUrl())
				.imageUrl(request.imageUrl())
				.build();

		return toDetail(artworkRepository.save(artwork));
	}

	public ArtworkPageResponse getArtworks(ArtworkSearchCondition condition, Pageable pageable) {
		Page<Artwork> page = artworkRepository.findAll(ArtworkSpecifications.matches(condition), pageable);
		return ArtworkPageResponse.of(page, findArtistNames(page), findAuctionIds(page));
	}

	// findArtistNames()랑 똑같은 패턴 — 작품마다 따로 조회 안 하고 한 번에 가져옴
	private Map<Long, Long> findAuctionIds(Page<Artwork> page) {
		Set<Long> artworkIds = page.getContent().stream().map(Artwork::getId).collect(Collectors.toSet());
		if (artworkIds.isEmpty()) {
			return Map.of();
		}
		return auctionRepository.findByArtworkIdIn(artworkIds).stream()
				.collect(Collectors.toMap(Auction::getArtworkId, Auction::getId, Long::max));
	}

	public ArtworkDetailResponse getArtwork(Long artworkId) {
		return toDetail(findArtwork(artworkId));
	}

	@Transactional
	public ArtworkDetailResponse update(Long artworkId, ArtworkUpdateRequest request) {
		Artwork artwork = findArtwork(artworkId);
		if (request.artistId() != null) {
			requireArtistExists(request.artistId());
		}

		artwork.update(request.toChanges());
		artworkRepository.flush(); // updatedAt(@PreUpdate)을 응답에 반영하기 위해 즉시 flush
		return toDetail(artwork);
	}

	private ArtworkDetailResponse toDetail(Artwork artwork) {
		// 이 API 이전에 만들어진 작품은 artistId가 없을 수 있다
		Artist artist = artwork.getArtistId() == null ? null
				: artistRepository.findById(artwork.getArtistId()).orElse(null);
		return ArtworkDetailResponse.of(artwork, artist,
				artworkMediaRepository.findByArtworkIdOrderBySortOrder(artwork.getId()));
	}

	// 목록의 작가 이름을 작품마다 조회하지 않고 한 번에 가져온다 (N+1 방지)
	private Map<Long, String> findArtistNames(Page<Artwork> page) {
		Set<Long> artistIds = page.getContent().stream()
				.map(Artwork::getArtistId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (artistIds.isEmpty()) {
			return Map.of();
		}
		return artistRepository.findAllById(artistIds).stream()
				.collect(Collectors.toMap(Artist::getId, Artist::getName));
	}

	private Artwork findArtwork(Long artworkId) {
		return artworkRepository.findById(artworkId).orElseThrow(() -> new ArtworkNotFoundException(artworkId));
	}

	private void requireArtistExists(Long artistId) {
		if (!artistRepository.existsById(artistId)) {
			throw new ArtistNotFoundException(artistId);
		}
	}

	@Transactional
	public void approve(Long artworkId){
		Artwork artwork = artworkRepository.findById(artworkId)
				.orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다. " + artworkId));
		artwork.approve();
	}

	@Transactional
	public void reject(Long artworkId){
		Artwork artwork = artworkRepository.findById(artworkId)
				.orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다. " + artworkId));
		artwork.reject();
	}
}
