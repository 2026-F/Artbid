package com.artbid.artwork.dto;

import com.artbid.artwork.domain.Artwork;
import com.artbid.media.domain.ArtworkMedia;
import com.artbid.media.domain.MediaType;
import java.util.Comparator;
import java.util.List;

/** 목록과 상세에서 동일한 대표 이미지를 선택한다. */
public final class ArtworkImages {
    private ArtworkImages() {}

    public static String primary(Artwork artwork, List<ArtworkMedia> media) {
        return media.stream()
                .filter(item -> item.getMediaType() == MediaType.PHOTO)
                .filter(item -> item.getUrl() != null && !item.getUrl().isBlank())
                .sorted(Comparator.comparing(ArtworkMedia::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ArtworkMedia::getId, Comparator.nullsLast(Long::compareTo)))
                .map(ArtworkMedia::getUrl).findFirst().orElse(artwork.getImageUrl());
    }
}
