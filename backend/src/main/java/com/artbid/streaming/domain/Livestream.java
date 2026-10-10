package com.artbid.streaming.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 new로 필드 다 채우는 걸 막음
@Builder
public class Livestream {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long auctionId;

	// AWS IVS Real-Time 스테이지 식별자. 참여 토큰을 발급할 때 이 값이 필요하다.
	private String stageArn;

	@Enumerated(EnumType.STRING)
	private LivestreamStatus status;

	private LocalDateTime startedAt;
	private LocalDateTime endedAt;

	/**
	 * 새 스테이지로 (재)활성화한다. 처음 시작할 때뿐 아니라, 한 번 끝난(ENDED) 경매를
	 * 다시 시작할 때도 같은 행을 재사용해서 새 스테이지 정보로 덮어쓴다.
	 */
	public void activate(String stageArn, LocalDateTime now) {
		this.stageArn = stageArn;
		this.status = LivestreamStatus.LIVE;
		this.startedAt = now;
		this.endedAt = null;
	}

	/** AWS IVS 쪽 스테이지를 삭제한 뒤 상태를 반영한다. */
	public void markEnded(LocalDateTime now) {
		this.status = LivestreamStatus.ENDED;
		this.endedAt = now;
	}

	public boolean isLive() {
		return status == LivestreamStatus.LIVE;
	}
}
