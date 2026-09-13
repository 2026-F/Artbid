package com.artbid.streaming.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

	// AWS IVS 채널 리소스 식별자
	private String ivsChannelArn;
	private String streamKey;
	private String ingestEndpoint; // streamKey와 합쳐 위탁자가 OBS 등에 등록할 RTMP(S) 주소가 됨
	private String playbackUrl;

	@Enumerated(EnumType.STRING)
	private LivestreamStatus status;

	/** AWS IVS 쪽 스트림을 강제 종료한 뒤 상태를 반영한다. */
	public void markEnded() {
		this.status = LivestreamStatus.ENDED;
	}
}
