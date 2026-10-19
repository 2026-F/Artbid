package com.artbid.artwork.repository;

import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;

import java.util.Set;

/**
 * 작품 목록 검색·필터 조건. null인 조건은 적용하지 않는다.
 *
 * @param keyword  작품 제목 또는 작가 이름 부분 일치 (대소문자 무시)
 * @param statuses 포함할 상태 목록
 * @param minPrice 시작가 하한 (이상)
 * @param maxPrice 시작가 상한 (이하)
 */
public record ArtworkSearchCondition(String keyword, Long artistId, ArtworkCategory category,
		Set<ArtworkStatus> statuses, Long minPrice, Long maxPrice) {
}
