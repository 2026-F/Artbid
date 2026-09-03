package com.artbid.media.controller;

import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/artworks/{artworkId}/media")
@RequiredArgsConstructor
public class MediaController {

	private final MediaService mediaService;

	@PostMapping("/presigned-url")
	public String issuePresignedUrl(@PathVariable Long artworkId, @RequestParam String fileName,
			@RequestParam String contentType) {
		return mediaService.issuePresignedUrl(artworkId, fileName, contentType);
	}

	@PostMapping("/complete")
	public void completeUpload(@PathVariable Long artworkId, @RequestParam String objectKey) {
		mediaService.completeUpload(artworkId, objectKey);
	}

	@GetMapping
	public List<ArtworkMedia> getMediaList(@PathVariable Long artworkId) {
		return mediaService.getMediaList(artworkId);
	}
}
