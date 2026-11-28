package com.artbid.liveshow.domain;

import com.artbid.liveshow.exception.LiveShowConflictException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 위탁자가 여러 경매(작품)를 묶어 차례로 진행하는 라이브 한 회차. 예: 올해의작가특별전.
 * 작품 차례는 LiveShowLot에 있고, 각 차례의 입찰과 마감은 기존 Auction이 그대로 맡는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "live_show", indexes = @Index(name = "idx_live_show_status", columnList = "status"))
public class LiveShow {

	public static final int MAX_TITLE_LENGTH = 100;
	public static final int MAX_DESCRIPTION_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 진행자(위탁자) 회원 ID */
	@Column(nullable = false)
	private Long hostId;

	@Column(nullable = false, length = MAX_TITLE_LENGTH)
	private String title;

	@Column(length = MAX_DESCRIPTION_LENGTH)
	private String description;

	/** 안내용 예정 시각. 실제 시작은 진행자가 한다. */
	private LocalDateTime scheduledAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LiveShowStatus status;

	/** 지금 진행 중이거나 마지막으로 진행한 차례. 아직 하나도 열지 않았으면 null. */
	private Long currentLotId;

	// AWS IVS 스테이지 식별자. AWS 계정 정보가 들어 있어 응답에 내보내지 않는다.
	private String stageArn;

	private LocalDateTime startedAt;
	private LocalDateTime endedAt;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static LiveShow create(Long hostId, String title, String description, LocalDateTime scheduledAt,
			LocalDateTime now) {
		if (hostId == null) {
			throw new IllegalArgumentException("진행자는 필수입니다.");
		}
		String trimmedTitle = title == null ? "" : title.trim();
		if (trimmedTitle.isEmpty() || trimmedTitle.length() > MAX_TITLE_LENGTH) {
			throw new IllegalArgumentException("라이브 제목은 1자 이상 " + MAX_TITLE_LENGTH + "자 이하여야 합니다.");
		}
		String trimmedDescription = description == null || description.isBlank() ? null : description.trim();
		if (trimmedDescription != null && trimmedDescription.length() > MAX_DESCRIPTION_LENGTH) {
			throw new IllegalArgumentException("라이브 소개는 " + MAX_DESCRIPTION_LENGTH + "자 이하여야 합니다.");
		}
		LiveShow show = new LiveShow();
		show.hostId = hostId;
		show.title = trimmedTitle;
		show.description = trimmedDescription;
		show.scheduledAt = scheduledAt;
		show.status = LiveShowStatus.SCHEDULED;
		show.createdAt = now;
		return show;
	}

	public boolean isHost(Long memberId) {
		return memberId != null && memberId.equals(hostId);
	}

	public boolean isLive() {
		return status == LiveShowStatus.LIVE;
	}

	public void start(String stageArn, LocalDateTime now) {
		if (status != LiveShowStatus.SCHEDULED) {
			throw new LiveShowConflictException("시작 전 라이브만 시작할 수 있습니다. 현재 상태: " + status);
		}
		this.stageArn = stageArn;
		this.status = LiveShowStatus.LIVE;
		this.startedAt = now;
	}

	public void end(LocalDateTime now) {
		if (status != LiveShowStatus.LIVE) {
			throw new LiveShowConflictException("진행 중인 라이브만 종료할 수 있습니다. 현재 상태: " + status);
		}
		this.status = LiveShowStatus.ENDED;
		this.endedAt = now;
	}

	public void moveTo(Long lotId) {
		this.currentLotId = lotId;
	}
}
