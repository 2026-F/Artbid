package com.artbid.artwork.service;

import com.artbid.artist.repository.ArtistRepository;
import com.artbid.artwork.domain.Artwork;
import com.artbid.artwork.domain.ArtworkCategory;
import com.artbid.artwork.domain.ArtworkStatus;
import com.artbid.artwork.dto.ArtworkImages;
import com.artbid.artwork.repository.*;
import com.artbid.auction.domain.Auction;
import com.artbid.auction.repository.AuctionRepository;
import com.artbid.media.domain.*;
import com.artbid.media.repository.ArtworkMediaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArtworkReadContractTest {
    @Test
    void listAndDetailShareHighestAuctionIdAndOrderedPhotoWithoutPerItemMediaQueries() {
        var artworks = mock(ArtworkRepository.class);
        var artists = mock(ArtistRepository.class);
        var media = mock(ArtworkMediaRepository.class);
        var auctions = mock(AuctionRepository.class);
        var service = new ArtworkService(artworks, artists, media, auctions);
        var artwork = Artwork.builder().consignorId(1L).artistId(2L).title("개발 작품")
                .category(ArtworkCategory.PAINTING).startPrice(100000L).imageUrl("fallback.jpg").build();
        ReflectionTestUtils.setField(artwork, "id", 10L);
        var older = mock(Auction.class); when(older.getId()).thenReturn(3L); when(older.getArtworkId()).thenReturn(10L);
        var latest = mock(Auction.class); when(latest.getId()).thenReturn(25L); when(latest.getArtworkId()).thenReturn(10L);
        var photos = List.of(new ArtworkMedia(10L, MediaType.PHOTO, "second.jpg", null, 2),
                new ArtworkMedia(10L, MediaType.VIDEO, "video.mp4", null, 0),
                new ArtworkMedia(10L, MediaType.PHOTO, " ", null, 0),
                new ArtworkMedia(10L, MediaType.PHOTO, "first.jpg", null, 1));
        when(artworks.findById(10L)).thenReturn(Optional.of(artwork));
        when(artworks.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artwork), PageRequest.of(0, 2), 1));
        when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of(latest, older));
        when(media.findByArtworkIdOrderBySortOrder(10L)).thenReturn(photos);
        when(media.findByArtworkIdInOrderBySortOrderAscIdAsc(anyCollection())).thenReturn(photos);
        var condition = new ArtworkSearchCondition(null, null, null, Set.of(ArtworkStatus.PREVIEW), null, null);
        var page = service.getArtworks(condition, PageRequest.of(0, 2));
        verify(media, never()).findByArtworkIdOrderBySortOrder(anyLong());
        verify(media).findByArtworkIdInOrderBySortOrderAscIdAsc(List.of(10L));
        var detail = service.getArtwork(10L);
        assertThat(page.content().getFirst().auctionId()).isEqualTo(25L).isEqualTo(detail.auctionId());
        assertThat(page.content().getFirst().imageUrl()).isEqualTo("first.jpg").isEqualTo(detail.imageUrl());
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        when(auctions.findByArtworkIdIn(anyCollection())).thenReturn(List.of());
        when(media.findByArtworkIdOrderBySortOrder(10L)).thenReturn(List.of());
        assertThat(service.getArtwork(10L).auctionId()).isNull();
        assertThat(service.getArtwork(10L).imageUrl()).isEqualTo("fallback.jpg");
        assertThat(ArtworkImages.primary(artwork, List.of(new ArtworkMedia(10L, MediaType.VIDEO, "video.mp4", null, 0))))
                .isEqualTo("fallback.jpg");
    }
}
