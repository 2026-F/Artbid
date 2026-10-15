package com.artbid.artwork.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Artwork {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long consignorId;
	private String title;
	private Long startPrice;
	private Long estimatedPrice;
	private String certificateUrl;
	private String imageUrl;

	// 작품 실제 크기 (cm) - 공간 배치 미리보기에서 실물 비율 계산용
	private Double widthCm;
	private Double heightCm;

	@Enumerated(EnumType.STRING)
	private ArtworkStatus status;
}
