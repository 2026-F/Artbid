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

	@Enumerated(EnumType.STRING)
	private ArtworkStatus status;
}
