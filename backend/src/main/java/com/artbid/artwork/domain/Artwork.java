package com.artbid.artwork.domain;

import com.artbid.artwork.exception.ArtworkNotEditableException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 위탁 작품.
 * 등록 직후에는 심사 대기(PENDING_REVIEW) 상태이며, 상태 전환(승인/반려)은 심사 로직(#36)에서 담당한다.
 * 기존 데이터가 있는 테이블에 컬럼이 추가되므로(ddl-auto: update) 새 컬럼은 DB에서 nullable로 두고
 * 필수 여부는 도메인/요청 DTO에서 검증한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artwork {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long consignorId;
	private Long artistId;

	@Column(length = 100)
	private String title;

	@Column(length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	private ArtworkCategory category;

	// 재료·기법 (예: 캔버스에 유채)
	@Column(length = 100)
	private String medium;

	private Double widthCm;
	private Double heightCm;
	private Double depthCm;
	private Integer productionYear;

	private Long startPrice;
	private Long estimatedPrice;

	@Column(length = 500)
	private String certificateUrl;

	// 목록 썸네일용 대표 이미지 (상세 미디어는 ArtworkMedia)
	@Column(length = 500)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	private ArtworkStatus status;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	@Builder
	private Artwork(Long consignorId, Long artistId, String title, String description, ArtworkCategory category,
			String medium, Double widthCm, Double heightCm, Double depthCm, Integer productionYear, Long startPrice,
			Long estimatedPrice, String certificateUrl, String imageUrl) {
		this.consignorId = requireNonNull(consignorId, "위탁자");
		this.artistId = requireNonNull(artistId, "작가");
		this.title = requireTitle(title);
		this.description = description;
		this.category = requireNonNull(category, "카테고리");
		this.medium = medium;
		this.widthCm = widthCm;
		this.heightCm = heightCm;
		this.depthCm = depthCm;
		this.productionYear = productionYear;
		this.startPrice = requirePositive(requireNonNull(startPrice, "시작가"), "시작가");
		this.estimatedPrice = estimatedPrice == null ? null : requirePositive(estimatedPrice, "추정가");
		this.certificateUrl = certificateUrl;
		this.imageUrl = imageUrl;
		this.status = ArtworkStatus.PENDING_REVIEW;
	}

	/**
	 * 부분 수정 — null로 들어온 필드는 기존 값을 유지한다.
	 * 심사 대기·반려 상태에서만 수정할 수 있다 (프리뷰·경매 중·낙찰 이후에는 입찰자 보호를 위해 수정 불가).
	 */
	public void update(Changes changes) {
		if (!status.isEditable()) {
			throw new ArtworkNotEditableException(id, status);
		}
		if (changes.artistId() != null) {
			this.artistId = changes.artistId();
		}
		if (changes.title() != null) {
			this.title = requireTitle(changes.title());
		}
		if (changes.description() != null) {
			this.description = changes.description();
		}
		if (changes.category() != null) {
			this.category = changes.category();
		}
		if (changes.medium() != null) {
			this.medium = changes.medium();
		}
		if (changes.widthCm() != null) {
			this.widthCm = changes.widthCm();
		}
		if (changes.heightCm() != null) {
			this.heightCm = changes.heightCm();
		}
		if (changes.depthCm() != null) {
			this.depthCm = changes.depthCm();
		}
		if (changes.productionYear() != null) {
			this.productionYear = changes.productionYear();
		}
		if (changes.startPrice() != null) {
			this.startPrice = requirePositive(changes.startPrice(), "시작가");
		}
		if (changes.estimatedPrice() != null) {
			this.estimatedPrice = requirePositive(changes.estimatedPrice(), "추정가");
		}
		if (changes.certificateUrl() != null) {
			this.certificateUrl = changes.certificateUrl();
		}
		if (changes.imageUrl() != null) {
			this.imageUrl = changes.imageUrl();
		}
	}

	public record Changes(Long artistId, String title, String description, ArtworkCategory category, String medium,
			Double widthCm, Double heightCm, Double depthCm, Integer productionYear, Long startPrice,
			Long estimatedPrice, String certificateUrl, String imageUrl) {
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	private static <T> T requireNonNull(T value, String field) {
		if (value == null) {
			throw new IllegalArgumentException(field + "은(는) 필수입니다");
		}
		return value;
	}

	public void approve(){
		if(status != ArtworkStatus.PENDING_REVIEW){
			throw new ArtworkNotEditableException(id, status);
		}
		this.status = ArtworkStatus.PREVIEW;
	}

	public void reject(){
		if(status != ArtworkStatus.PENDING_REVIEW){
			throw new ArtworkNotEditableException(id, status);
		}

		this.status = ArtworkStatus.REJECTED;
	}

	private static String requireTitle(String title) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("작품 제목은 비어 있을 수 없습니다");
		}
		return title.trim();
	}

	private static Long requirePositive(Long value, String field) {
		if (value <= 0) {
			throw new IllegalArgumentException(field + "는 0보다 커야 합니다");
		}
		return value;
	}
}
