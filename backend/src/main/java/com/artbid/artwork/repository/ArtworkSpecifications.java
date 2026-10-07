package com.artbid.artwork.repository;

import com.artbid.artist.domain.Artist;
import com.artbid.artwork.domain.Artwork;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ArtworkSpecifications {

	private static final char LIKE_ESCAPE = '\\';

	private ArtworkSpecifications() {
	}

	public static Specification<Artwork> matches(ArtworkSearchCondition condition) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (condition.statuses() != null && !condition.statuses().isEmpty()) {
				predicates.add(root.get("status").in(condition.statuses()));
			}
			if (condition.artistId() != null) {
				predicates.add(cb.equal(root.get("artistId"), condition.artistId()));
			}
			if (condition.category() != null) {
				predicates.add(cb.equal(root.get("category"), condition.category()));
			}
			if (condition.minPrice() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.<Long>get("startPrice"), condition.minPrice()));
			}
			if (condition.maxPrice() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.<Long>get("startPrice"), condition.maxPrice()));
			}
			if (condition.keyword() != null && !condition.keyword().isBlank()) {
				String pattern = "%" + escapeLike(condition.keyword().trim().toLowerCase()) + "%";

				// Artwork는 artistId만 들고 있으므로 작가 이름 검색은 서브쿼리로 처리
				Subquery<Long> artistIds = query.subquery(Long.class);
				Root<Artist> artist = artistIds.from(Artist.class);
				artistIds.select(artist.get("id"))
						.where(cb.like(cb.lower(artist.<String>get("name")), pattern, LIKE_ESCAPE));

				predicates.add(cb.or(
						cb.like(cb.lower(root.<String>get("title")), pattern, LIKE_ESCAPE),
						root.get("artistId").in(artistIds)));
			}

			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	// 검색어에 들어온 %, _ 가 와일드카드로 동작하지 않도록 이스케이프
	private static String escapeLike(String keyword) {
		return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
