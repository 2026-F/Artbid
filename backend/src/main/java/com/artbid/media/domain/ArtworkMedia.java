package com.artbid.media.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class ArtworkMedia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long artworkId;

	@Enumerated(EnumType.STRING)
	private MediaType mediaType;

	private String url;

	// 동영상 트랜스코딩 완료 전까지 참조할 원본 URL (MediaConvert 처리 후 url이 채워짐)
	private String sourceUrl;

	private Integer sortOrder;
}
