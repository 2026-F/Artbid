package com.artbid.artist.dto;

import com.artbid.artist.domain.Artist;

import java.time.LocalDateTime;

public record ArtistResponse(Long id, String name, String biography, Integer birthYear, String nationality,
		String profileImageUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {

	public static ArtistResponse from(Artist artist) {
		return new ArtistResponse(artist.getId(), artist.getName(), artist.getBiography(), artist.getBirthYear(),
				artist.getNationality(), artist.getProfileImageUrl(), artist.getCreatedAt(), artist.getUpdatedAt());
	}
}
