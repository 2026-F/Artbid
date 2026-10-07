package com.artbid.artist.service;

import com.artbid.artist.domain.Artist;
import com.artbid.artist.dto.ArtistCreateRequest;
import com.artbid.artist.dto.ArtistPageResponse;
import com.artbid.artist.dto.ArtistResponse;
import com.artbid.artist.dto.ArtistUpdateRequest;
import com.artbid.artist.exception.ArtistNotFoundException;
import com.artbid.artist.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistService {

	private final ArtistRepository artistRepository;

	@Transactional
	public ArtistResponse register(ArtistCreateRequest request) {
		Artist artist = new Artist(request.name(), request.biography(), request.birthYear(), request.nationality(),
				request.profileImageUrl());
		return ArtistResponse.from(artistRepository.save(artist));
	}

	// keyword가 있으면 이름 부분 일치(대소문자 무시)로 검색
	public ArtistPageResponse getArtists(String keyword, Pageable pageable) {
		Page<Artist> page = (keyword == null || keyword.isBlank())
				? artistRepository.findAll(pageable)
				: artistRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
		return ArtistPageResponse.from(page);
	}

	public ArtistResponse getArtist(Long artistId) {
		return ArtistResponse.from(findArtist(artistId));
	}

	@Transactional
	public ArtistResponse update(Long artistId, ArtistUpdateRequest request) {
		Artist artist = findArtist(artistId);
		artist.update(request.name(), request.biography(), request.birthYear(), request.nationality(),
				request.profileImageUrl());
		artistRepository.flush(); // updatedAt(@PreUpdate)을 응답에 반영하기 위해 즉시 flush
		return ArtistResponse.from(artist);
	}

	private Artist findArtist(Long artistId) {
		return artistRepository.findById(artistId).orElseThrow(() -> new ArtistNotFoundException(artistId));
	}
}
