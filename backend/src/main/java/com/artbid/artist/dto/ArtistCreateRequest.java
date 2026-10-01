package com.artbid.artist.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtistCreateRequest(
		@NotBlank @Size(max = 50) String name,
		@Size(max = 2000) String biography,
		@Min(1000) @Max(9999) Integer birthYear,
		@Size(max = 50) String nationality,
		@Size(max = 500) String profileImageUrl) {
}
