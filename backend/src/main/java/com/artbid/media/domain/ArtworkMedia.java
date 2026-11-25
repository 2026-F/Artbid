package com.artbid.media.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
		indexes = @Index(name = "idx_artwork_media_artwork", columnList = "artworkId, sortOrder, id"),
		uniqueConstraints = @UniqueConstraint(name = "uk_artwork_media_artwork_object_key",
				columnNames = {"artwork_id", "object_key"})
)
@Getter
@NoArgsConstructor
public class ArtworkMedia {

	private static final int FAILURE_REASON_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long artworkId;

	// 기존 행은 완료 재시도에서 키를 확인한 경우에만 채운다.
	@JsonIgnore
	@Column(name = "object_key", updatable = false)
	private String objectKey;

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

	// 변환 요청 처리권 만료 시각
	@JsonIgnore
	private Instant transcodeClaimUntil;

	// AWS 요청을 보내기 전에 기록한다. 응답이 유실되면 이 시점부터 작업 이력을 확인한다.
	@JsonIgnore
	private Instant transcodeFirstAttemptAt;

	@Column(length = FAILURE_REASON_MAX_LENGTH)
	private String transcodeFailureReason;

	public ArtworkMedia(Long artworkId, MediaType mediaType, String url, String sourceUrl, Integer sortOrder) {
		this(artworkId, mediaType, url, sourceUrl, sortOrder, null);
	}

	public ArtworkMedia(Long artworkId, MediaType mediaType, String url, String sourceUrl, Integer sortOrder,
			String objectKey) {
		this.artworkId = artworkId;
		this.objectKey = objectKey;
		this.mediaType = mediaType;
		this.url = url;
		this.sourceUrl = sourceUrl;
		this.sortOrder = sortOrder;
	}

	public void updateUrl(String url) {
		this.url = url;
	}

	public void queueTranscode() {
		this.transcodeStatus = TranscodeStatus.PROCESSING;
	}

	public void claimTranscode(Instant now, Instant until) {
		this.transcodeClaimUntil = until;
		if (this.transcodeFirstAttemptAt == null) {
			this.transcodeFirstAttemptAt = now;
		}
	}

	public void finishTranscodeSubmission() {
		this.transcodeClaimUntil = null;
		this.transcodeFirstAttemptAt = null;
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
