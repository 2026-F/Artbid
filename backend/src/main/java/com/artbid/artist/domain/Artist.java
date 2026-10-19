package com.artbid.artist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 작품을 만든 작가 정보.
 * 위탁자(판매자, Member.CONSIGNOR)와는 별개로 관리한다 — 갤러리가 여러 작가의 작품을 위탁할 수 있기 때문.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artist {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(length = 2000)
	private String biography;

	private Integer birthYear;

	@Column(length = 50)
	private String nationality;

	@Column(length = 500)
	private String profileImageUrl;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public Artist(String name, String biography, Integer birthYear, String nationality, String profileImageUrl) {
		this.name = requireName(name);
		this.biography = biography;
		this.birthYear = birthYear;
		this.nationality = nationality;
		this.profileImageUrl = profileImageUrl;
	}

	/**
	 * 부분 수정 — null로 들어온 필드는 기존 값을 유지한다.
	 */
	public void update(String name, String biography, Integer birthYear, String nationality, String profileImageUrl) {
		if (name != null) {
			this.name = requireName(name);
		}
		if (biography != null) {
			this.biography = biography;
		}
		if (birthYear != null) {
			this.birthYear = birthYear;
		}
		if (nationality != null) {
			this.nationality = nationality;
		}
		if (profileImageUrl != null) {
			this.profileImageUrl = profileImageUrl;
		}
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

	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("작가 이름은 비어 있을 수 없습니다");
		}
		return name.trim();
	}
}
