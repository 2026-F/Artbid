package com.artbid.artwork.controller;

import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.service.ArtworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/artworks")
@RequiredArgsConstructor
public class ArtworkController {

	private final ArtworkService artworkService;

	@GetMapping
	public List<Artwork> getPreviewArtworks() {
		return artworkService.getPreviewArtworks();
	}
}
