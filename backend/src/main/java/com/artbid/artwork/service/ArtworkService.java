package com.artbid.artwork.service;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.repository.ArtworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtworkService {

	private final ArtworkRepository artworkRepository;

	public List<Artwork> getPreviewArtworks() {
		// TODO: status = PREVIEW 조건으로 조회하도록 쿼리 메서드 추가
		return artworkRepository.findAll();
	}
}
