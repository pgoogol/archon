package com.pgoogol.music.library;

import com.pgoogol.music.catalog.CatalogSearchCriteria;
import com.pgoogol.music.catalog.CatalogService;
import com.pgoogol.music.catalog.CatalogSortOrder;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Wyszukiwarka biblioteki dokłada do wyniku z katalogu dane prywatne DJ-a.
 * Sedno jest w tym, że dokłada je JEDNYM zapytaniem po ID-kach jednej strony
 * — i że utwór bez wpisu zostaje w wyniku z pustym {@code entry}, zamiast
 * z niego wypaść.
 */
@ExtendWith(MockitoExtension.class)
class LibrarySearchServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final String IN_LIBRARY = "4uLU6hMCjMI75M1A2tKUQC";
    private static final String NOT_IN_LIBRARY = "1nZzLMBiPPUlYAcZDkCsSN";

    @Mock
    private CatalogService catalogService;

    @Mock
    private LibraryEntryRepository libraryEntryRepository;

    @InjectMocks
    private LibrarySearchService service;

    @Test
    @DisplayName("utwór bez wpisu bibliotecznego zostaje w wyniku z pustym entry")
    void search_whenTrackHasNoLibraryEntry_keepsRowWithEmptyEntry() {

        // given
        TrackCatalog owned = TrackCatalogFixtures.enrichedTrack(IN_LIBRARY);
        TrackCatalog foreign = TrackCatalogFixtures.skeletonTrack(NOT_IN_LIBRARY);
        givenCatalogPage(owned, foreign);
        given(libraryEntryRepository.findWithTrackByTrackSpotifyIdIn(
            List.of(IN_LIBRARY, NOT_IN_LIBRARY)))
            .willReturn(List.of(entryFor(owned)));

        // when
        Page<LibraryRow> rows = service.search(
            CatalogSearchCriteria.none(), CatalogSortOrder.DEFAULT, PAGE);

        // then
        assertThat(rows.getContent()).hasSize(2);
        assertThat(rows.getContent().get(0).libraryEntry()).isPresent();
        assertThat(rows.getContent().get(1).libraryEntry()).isEmpty();
    }

    @Test
    @DisplayName("wpis biblioteczny trafia do wiersza właściwego utworu")
    void search_matchesLibraryEntryToItsOwnTrack() {

        // given
        TrackCatalog owned = TrackCatalogFixtures.enrichedTrack(IN_LIBRARY);
        TrackCatalog other = TrackCatalogFixtures.skeletonTrack(NOT_IN_LIBRARY);
        LibraryEntry entry = entryFor(owned);
        givenCatalogPage(other, owned);
        given(libraryEntryRepository.findWithTrackByTrackSpotifyIdIn(
            List.of(NOT_IN_LIBRARY, IN_LIBRARY)))
            .willReturn(List.of(entry));

        // when
        Page<LibraryRow> rows = service.search(
            CatalogSearchCriteria.none(), CatalogSortOrder.DEFAULT, PAGE);

        // then
        assertThat(rows.getContent().get(1).entry()).isSameAs(entry);
        assertThat(rows.getContent().get(0).entry()).isNull();
    }

    @Test
    @DisplayName("pusta strona wyniku nie odpytuje biblioteki wcale")
    void search_whenCatalogPageIsEmpty_skipsLibraryQuery() {

        // given
        given(catalogService.search(any(), any(), any())).willReturn(Page.empty(PAGE));

        // when
        Page<LibraryRow> rows = service.search(
            CatalogSearchCriteria.none(), CatalogSortOrder.DEFAULT, PAGE);

        // then
        assertThat(rows).isEmpty();
        verify(libraryEntryRepository, never()).findWithTrackByTrackSpotifyIdIn(any());
    }

    @Test
    @DisplayName("kryteria i porządek idą do katalogu bez zmian")
    void search_delegatesCriteriaAndSortOrderToCatalog() {

        // given
        CatalogSearchCriteria criteria = CatalogSearchCriteria.none();
        CatalogSortOrder sortOrder = CatalogSortOrder.DEFAULT;
        given(catalogService.search(criteria, sortOrder, PAGE)).willReturn(Page.empty(PAGE));

        // when
        service.search(criteria, sortOrder, PAGE);

        // then
        verify(catalogService).search(criteria, sortOrder, PAGE);
    }

    @Test
    @DisplayName("search odrzuca brak kryteriów")
    void search_whenCriteriaIsNull_throwsNullPointerException() {

        // when / then
        assertThatThrownBy(() -> service.search(null, CatalogSortOrder.DEFAULT, PAGE))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("criteria");
    }

    private void givenCatalogPage(TrackCatalog... tracks) {

        given(catalogService.search(any(), any(), any()))
            .willReturn(new PageImpl<>(List.of(tracks), PAGE, tracks.length));
    }

    private LibraryEntry entryFor(TrackCatalog track) {

        LibraryEntry entry = new LibraryEntry(track, LibrarySource.FILE);
        entry.setRating(5);
        return entry;
    }
}
