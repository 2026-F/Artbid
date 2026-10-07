package com.artbid.media.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class ArtworkMedia {

	private static final int FAILURE_REASON_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long artworkId;

	@Enumerated(EnumType.STRING)
	private MediaType mediaType;

	// 감상용 URL. 동영상은 트랜스코딩이 끝나야(또는 건너뛰거나 실패해야) 채워진다.
	private String url;

	// 동영상 원본 URL (트랜스코딩 전·실패 시 참조)
	private String sourceUrl;

	private Integer sortOrder;

	@Enumerated(EnumType.STRING)
	private TranscodeStatus transcodeStatus;

	@Column(length = 100)
	private String transcodeJobId;

	@Column(length = FAILURE_REASON_MAX_LENGTH)
	private String transcodeFailureReason;

	public ArtworkMedia(Long artworkId, MediaType mediaType, String url, String sourceUrl, Integer sortOrder) {
		this.artworkId = artworkId;
		this.mediaType = mediaType;
		this.url = url;
		this.sourceUrl = sourceUrl;
		this.sortOrder = sortOrder;
	}

	public void updateUrl(String url) {
		this.url = url;
	}

	public void startTranscode(String jobId) {
		this.transcodeStatus = TranscodeStatus.PROCESSING;
		this.transcodeJobId = jobId;
	}

	// MediaConvert를 쓰지 않는 환경에서는 원본을 그대로 감상용으로 쓴다
	public void skipTranscode() {
		this.transcodeStatus = TranscodeStatus.SKIPPED;
		this.url = sourceUrl;
	}

	public void completeTranscode(String outputUrl) {
		if (transcodeStatus != TranscodeStatus.PROCESSING) {
			throw new IllegalStateException("트랜스코딩 중인 미디어가 아닙니다: " + id + " (" + transcodeStatus + ")");
		}
		this.transcodeStatus = TranscodeStatus.COMPLETED;
		this.url = outputUrl;
		this.transcodeFailureReason = null;
	}

	// 실패해도 원본으로는 재생할 수 있도록 url을 원본으로 채워둔다 (재시도 여부는 transcodeStatus로 판단)
	public void failTranscode(String reason) {
		this.transcodeStatus = TranscodeStatus.FAILED;
		this.transcodeFailureReason = truncate(reason);
		this.url = sourceUrl;
	}

	private static String truncate(String reason) {
		if (reason == null) {
			return null;
		}
		return reason.length() <= FAILURE_REASON_MAX_LENGTH ? reason : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
	}
}
