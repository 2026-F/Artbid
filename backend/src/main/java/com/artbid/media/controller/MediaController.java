package com.artbid.media.controller;

import com.artbid.member.auth.JwtAuthenticationFilter;
import com.artbid.member.domain.MemberRole;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.dto.PresignedUploadResponse;
import com.artbid.media.exception.MediaAuthRequiredException;
import com.artbid.media.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/artworks/{artworkId}/media")
@RequiredArgsConstructor
public class MediaController {

	private final MediaService mediaService;

	@PostMapping("/presigned-url")
	public PresignedUploadResponse issuePresignedUrl(@PathVariable Long artworkId, @RequestParam String fileName,
			@RequestParam String contentType, Principal principal, HttpServletRequest request) {
		return mediaService.issuePresignedUrl(artworkId, requireMemberId(principal), isAdmin(request), fileName,
				contentType);
	}

	// mediaType은 /presigned-url 응답의 mediaType 값을 그대로 전달한다 (PHOTO / VIDEO / MODEL_3D)
	@PostMapping("/complete")
	public void completeUpload(@PathVariable Long artworkId, @RequestParam String objectKey,
			@RequestParam String mediaType, Principal principal, HttpServletRequest request) {
		mediaService.completeUpload(artworkId, requireMemberId(principal), isAdmin(request), objectKey, mediaType);
	}

	@GetMapping
	public List<ArtworkMedia> getMediaList(@PathVariable Long artworkId) {
		return mediaService.getMediaList(artworkId);
	}

	private boolean isAdmin(HttpServletRequest request) {
		return request.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE) == MemberRole.ADMIN;
	}

	// Principal.name에는 JwtAuthenticationFilter가 검증한 Member ID가 들어 있다 (BidController와 같은 방식).
	private Long requireMemberId(Principal principal) {
		if (principal != null) {
			try {
				long id = Long.parseLong(principal.getName());
				if (id > 0) return id;
			} catch (NumberFormatException ignored) {
				// 아래에서 인증 필요 예외로 처리
			}
		}
		throw new MediaAuthRequiredException();
	}
}
