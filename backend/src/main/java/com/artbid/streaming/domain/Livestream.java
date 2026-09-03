package com.artbid.streaming.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Livestream {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long auctionId;

	// AWS IVS 채널 리소스 식별자
	private String ivsChannelArn;
	private String streamKey;
	private String playbackUrl;

	@Enumerated(EnumType.STRING)
	private LivestreamStatus status;
}
