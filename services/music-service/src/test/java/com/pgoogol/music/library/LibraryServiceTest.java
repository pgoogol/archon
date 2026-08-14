package com.pgoogol.music.library;

import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import com.pgoogol.music.common.ConflictException;
import com.pgoogol.music.common.NotFoundException;
import com.pgoogol.music.common.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Biblioteka DJ-a. Dwa miejsca warte pilnowania: dodanie utworu spoza katalogu
 * (D3 — szkielet katalogu powstaje raz i zostaje po usunięciu wpisu) oraz
 * aktualizacja, gdzie pusta wartość znaczy „wyczyść", a brak pola „nie ruszaj".
 */
@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    private static final String SPOTIFY_ID = "4uLU6hMCjMI75M1A2tKUQC";
    private static final int CURRENT_VERSION = 0;

    @Mock
    private LibraryEntryRepository libraryEntryRepository;

    @Mock
    private TrackCatalogRepository trackCatalogRepository;

    @Mock
    private LibraryOverviewRepository libraryOverviewRepository;

    @InjectMocks
    private LibraryService service;

    @Captor
    private ArgumentCaptor<TrackCatalog> trackCaptor;

    @Test
    @DisplayName("dodanie utworu, który już jest w bibliotece, kończy się konfliktem")
    void add_whenEntryAlreadyExists_throwsConflict() {

        // given
        given(libraryEntryRepository.existsByTrackSpotifyId(SPOTIFY_ID)).willReturn(true);

        // when
        Throwable thrown = catchThrowable(
            () -> service.add(SPOTIFY_ID, "Vivir Mi Vida", "Marc Anthony", "3.0"));

        // then
        assertThat(thrown).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) thrown).getErrorCode()).isEqualTo("LIBRARY_ENTRY_EXISTS");
        verify(libraryEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("utwór spoza katalogu dostaje szkielet z tytułem, artystą i albumem")
    void add_whenTrackNotInCatalog_savesSkeletonFromRequest() {

        // given
        given(libraryEntryRepository.existsByTrackSpotifyId(SPOTIFY_ID)).willReturn(false);
        given(trackCatalogRepository.findById(SPOTIFY_ID)).willReturn(Optional.empty());
        given(trackCatalogRepository.save(any())).willAnswer(call -> call.getArgument(0));
        given(libraryEntryRepository.save(any())).willAnswer(call -> call.getArgument(0));

        // when
        service.add(SPOTIFY_ID, "Vivir Mi Vida", "Marc Anthony", "3.0");

        // then
        verify(trackCatalogRepository).save(trackCaptor.capture());
        TrackCatalog skeleton = trackCaptor.getValue();
        assertThat(skeleton.getSpotifyId()).isEqualTo(SPOTIFY_ID);
        assertThat(skeleton.getTitle()).isEqualTo("Vivir Mi Vida");
        assertThat(skeleton.getArtist()).isEqualTo("Marc Anthony");
        assertThat(skeleton.getAlbum()).isEqualTo("3.0");
    }

    @Test
    @DisplayName("utwór obecny w katalogu nie jest nadpisywany szkieletem")
    void add_whenTrackInCatalog_reusesCatalogRecord() {

        // given — katalog trzyma dane wzbogacone; nadpisanie ich szkieletem
        // z formularza kasowałoby efekt pracy joba
        TrackCatalog enriched = TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID);
        given(libraryEntryRepository.existsByTrackSpotifyId(SPOTIFY_ID)).willReturn(false);
        given(trackCatalogRepository.findById(SPOTIFY_ID)).willReturn(Optional.of(enriched));
        given(libraryEntryRepository.save(any())).willAnswer(call -> call.getArgument(0));

        // when
        LibraryEntry entry = service.add(SPOTIFY_ID, "cokolwiek", "cokolwiek", "cokolwiek");

        // then
        verify(trackCatalogRepository, never()).save(any());
        assertThat(entry.getTrack()).isSameAs(enriched);
    }

    @Test
    @DisplayName("wpis dodany ręcznie ma źródło FILE")
    void add_marksEntrySourceAsFile() {

        // given
        given(libraryEntryRepository.existsByTrackSpotifyId(SPOTIFY_ID)).willReturn(false);
        given(trackCatalogRepository.findById(SPOTIFY_ID))
            .willReturn(Optional.of(TrackCatalogFixtures.skeletonTrack(SPOTIFY_ID)));
        given(libraryEntryRepository.save(any())).willAnswer(call -> call.getArgument(0));

        // when
        LibraryEntry entry = service.add(SPOTIFY_ID, "t", "a", null);

        // then
        assertThat(entry.getSource()).isEqualTo(LibrarySource.FILE);
    }

    @Test
    @DisplayName("nieświeża wersja z żądania blokuje zapis")
    void update_whenVersionIsStale_throwsResourceModified() {

        // given — klient widział wersję 7, w bazie jest 0
        givenEntry(entry());
        LibraryEntryUpdate update = new LibraryEntryUpdate("nowa notatka", null, null, null, 7);

        // when
        Throwable thrown = catchThrowable(() -> service.update(SPOTIFY_ID, update));

        // then
        assertThat(thrown).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) thrown).getErrorCode()).isEqualTo("RESOURCE_MODIFIED");
    }

    @Test
    @DisplayName("ocena 0 czyści ocenę zamiast zapisywać zero")
    void update_whenRatingIsZero_clearsRating() {

        // given
        LibraryEntry entry = entry();
        entry.setRating(4);
        givenEntry(entry);
        LibraryEntryUpdate update =
            new LibraryEntryUpdate(null, null, 0, null, CURRENT_VERSION);

        // when
        LibraryEntry updated = service.update(SPOTIFY_ID, update);

        // then
        assertThat(updated.getRating()).isNull();
    }

    @Test
    @DisplayName("ocena spoza zakresu 1–5 jest odrzucana")
    void update_whenRatingOutOfRange_throwsValidation() {

        // given
        givenEntry(entry());
        LibraryEntryUpdate update =
            new LibraryEntryUpdate(null, null, 9, null, CURRENT_VERSION);

        // when
        Throwable thrown = catchThrowable(() -> service.update(SPOTIFY_ID, update));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getErrorCode()).isEqualTo("RATING_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("pusta notatka czyści pole, zamiast zapisywać pusty tekst")
    void update_whenDjNotesAreBlank_clearsNotes() {

        // given
        LibraryEntry entry = entry();
        entry.setDjNotes("stara notatka");
        givenEntry(entry);
        LibraryEntryUpdate update =
            new LibraryEntryUpdate("   ", null, null, null, CURRENT_VERSION);

        // when
        LibraryEntry updated = service.update(SPOTIFY_ID, update);

        // then
        assertThat(updated.getDjNotes()).isNull();
    }

    @Test
    @DisplayName("pusta lista tagów czyści tagi")
    void update_whenCustomTagsAreEmpty_clearsTags() {

        // given
        LibraryEntry entry = entry();
        entry.setCustomTags(List.of("wesele"));
        givenEntry(entry);
        LibraryEntryUpdate update =
            new LibraryEntryUpdate(null, List.of(), null, null, CURRENT_VERSION);

        // when
        LibraryEntry updated = service.update(SPOTIFY_ID, update);

        // then
        assertThat(updated.getCustomTags()).isNull();
    }

    @Test
    @DisplayName("pominięte pole zostaje bez zmian — PATCH nie kasuje tego, o co nie pytał")
    void update_whenFieldIsAbsent_leavesItUntouched() {

        // given
        LibraryEntry entry = entry();
        entry.setDjNotes("zachowaj mnie");
        entry.setRating(3);
        givenEntry(entry);
        LibraryEntryUpdate update =
            new LibraryEntryUpdate(null, null, null, "PRIME_TIME", CURRENT_VERSION);

        // when
        LibraryEntry updated = service.update(SPOTIFY_ID, update);

        // then
        assertThat(updated.getDjNotes()).isEqualTo("zachowaj mnie");
        assertThat(updated.getRating()).isEqualTo(3);
        assertThat(updated.getDjSlotOverride()).isEqualTo("PRIME_TIME");
    }

    @Test
    @DisplayName("update odrzuca brak treści zmiany")
    void update_whenUpdateIsNull_throwsNullPointerException() {

        // when
        Throwable thrown = catchThrowable(() -> service.update(SPOTIFY_ID, null));

        // then
        assertThat(thrown)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("update");
    }

    @Test
    @DisplayName("usunięcie wpisu nie rusza katalogu")
    void delete_removesEntryOnly() {

        // given
        LibraryEntry entry = entry();
        givenEntry(entry);

        // when
        service.delete(SPOTIFY_ID);

        // then
        verify(libraryEntryRepository).delete(entry);
        verify(trackCatalogRepository, never()).delete(any());
    }

    @Test
    @DisplayName("operacja na utworze spoza biblioteki kończy się LIBRARY_ENTRY_NOT_FOUND")
    void get_whenEntryMissing_throwsNotFound() {

        // given
        given(libraryEntryRepository.findWithTrackByTrackSpotifyId(SPOTIFY_ID))
            .willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.get(SPOTIFY_ID));

        // then
        assertThat(thrown).isInstanceOf(NotFoundException.class);
        assertThat(((NotFoundException) thrown).getErrorCode())
            .isEqualTo("LIBRARY_ENTRY_NOT_FOUND");
    }

    @Test
    @DisplayName("przegląd bierze braki z tego samego zapytania co zakładka Wzbogacanie")
    void overview_feedsMissingCountsFromCatalog() {

        // given
        given(trackCatalogRepository.countMissingByGroup()).willReturn(missingCounts(7, 13, 21));

        // when
        service.overview();

        // then
        verify(libraryOverviewRepository).load(7L, 13L, 21L);
    }

    @Test
    @DisplayName("listTags oddaje tagi użyte w bibliotece")
    void listTags_returnsDistinctTags() {

        // given
        given(libraryEntryRepository.findDistinctTags()).willReturn(List.of("wesele", "poprawiny"));

        // when
        List<String> tags = service.listTags();

        // then
        assertThat(tags).containsExactly("wesele", "poprawiny");
    }

    private LibraryEntry entry() {

        return new LibraryEntry(TrackCatalogFixtures.enrichedTrack(SPOTIFY_ID), LibrarySource.FILE);
    }

    private void givenEntry(LibraryEntry entry) {

        given(libraryEntryRepository.findWithTrackByTrackSpotifyId(SPOTIFY_ID))
            .willReturn(Optional.of(entry));
    }

    private TrackCatalogRepository.MissingCounts missingCounts(long metadata, long audio, long ai) {

        return new TrackCatalogRepository.MissingCounts() {

            @Override
            public long getMetadata() {
                return metadata;
            }

            @Override
            public long getAudio() {
                return audio;
            }

            @Override
            public long getAi() {
                return ai;
            }
        };
    }
}
