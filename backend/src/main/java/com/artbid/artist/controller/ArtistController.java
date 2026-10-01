package com.artbid.artist.controller;

import com.artbid.artist.dto.ArtistCreateRequest;
import com.artbid.artist.dto.ArtistPageResponse;
import com.artbid.artist.dto.ArtistResponse;
import com.artbid.artist.dto.ArtistUpdateRequest;
import com.artbid.artist.service.ArtistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {

	private static final int MAX_PAGE_SIZE = 100;

	private final ArtistService artistService;

	@PostMapping
	public ResponseEntity<ArtistResponse> register(@Valid @RequestBody ArtistCreateRequest request) {
		ArtistResponse response = artistService.register(request);
		return ResponseEntity.created(URI.create("/api/artists/" + response.id())).body(response);
	}

	@GetMapping
	public ArtistPageResponse getArtists(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "id"));
		return artistService.getArtists(keyword, pageable);
	}

	@GetMapping("/{artistId}")
	public ArtistResponse getArtist(@PathVariable Long artistId) {
		return artistService.getArtist(artistId);
	}

	@PatchMapping("/{artistId}")
	public ArtistResponse update(@PathVariable Long artistId, @Valid @RequestBody ArtistUpdateRequest request) {
		return artistService.update(artistId, request);
	}
}
