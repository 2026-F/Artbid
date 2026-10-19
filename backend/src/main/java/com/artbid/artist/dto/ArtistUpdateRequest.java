package com.artbid.artist.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 작가 정보 부분 수정 요청 (PATCH). 보내지 않은(null) 필드는 기존 값을 유지한다.
 */
public record ArtistUpdateRequest(
		@Size(max = 50) @Pattern(regexp = ".*\\S.*", message = "이름은 공백일 수 없습니다") String name,
		@Size(max = 2000) String biography,
		@Min(1000) @Max(9999) Integer birthYear,
		@Size(max = 50) String nationality,
		@Size(max = 500) String profileImageUrl) {
}
