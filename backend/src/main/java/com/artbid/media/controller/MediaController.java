package com.artbid.media.controller;

import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.dto.PresignedUploadResponse;
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
	public PresignedUploadResponse issuePresignedUrl(@PathVariable Long artworkId, @RequestParam String fileName,
			@RequestParam String contentType) {
		return mediaService.issuePresignedUrl(artworkId, fileName, contentType);
	}

	// mediaType은 /presigned-url 응답의 mediaType 값을 그대로 전달한다 (PHOTO / VIDEO / MODEL_3D)
	@PostMapping("/complete")
	public void completeUpload(@PathVariable Long artworkId, @RequestParam String objectKey,
			@RequestParam String mediaType) {
		mediaService.completeUpload(artworkId, objectKey, mediaType);
	}

	@GetMapping
	public List<ArtworkMedia> getMediaList(@PathVariable Long artworkId) {
		return mediaService.getMediaList(artworkId);
	}
}
