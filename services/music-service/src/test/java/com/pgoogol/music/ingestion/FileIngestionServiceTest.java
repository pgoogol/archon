package com.pgoogol.music.ingestion;

import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogRepository;
import com.pgoogol.music.library.LibraryEntry;
import com.pgoogol.music.library.LibraryEntryRepository;
import com.pgoogol.music.library.LibrarySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Import CSV. Sedno to liczenie duplikatów: ten sam utwór dwa razy w pliku
 * i utwór już obecny w bibliotece mają wpaść do {@code alreadyExisted},
 * a nie zostać zaimportowane po raz drugi ani zgubione po cichu.
 */
@ExtendWith(MockitoExtension.class)
class FileIngestionServiceTest {

    private static final String FIRST = "4uLU6hMCjMI75M1A2tKUQC";
    private static final String SECOND = "1nZzLMBiPPUlYAcZDkCsSN";

    @Mock
    private CsvTrackParser parser;

    @Mock
    private TrackCatalogRepository trackCatalogRepository;

    @Mock
    private LibraryEntryRepository libraryEntryRepository;

    @InjectMocks
    private FileIngestionService service;

    @Captor
    private ArgumentCaptor<List<TrackCatalog>> skeletonsCaptor;

    @Captor
    private ArgumentCaptor<List<LibraryEntry>> entriesCaptor;

    @Test
    @DisplayName("nowe utwory trafiają do katalogu i biblioteki")
    void ingestFile_whenAllTracksAreNew_importsThemAll() {

        // given
        givenParsed(track(FIRST), track(SECOND));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        givenCatalogReferences();

        // when
        IngestReport report = service.ingestFile(csv());

        // then
        assertThat(report.imported()).isEqualTo(2);
        assertThat(report.alreadyExisted()).isZero();
    }

    @Test
    @DisplayName("ten sam utwór dwa razy w pliku liczy się jako już istniejący, nie jako import")
    void ingestFile_whenFileRepeatsTrack_countsRepeatAsAlreadyExisted() {

        // given — dwa wiersze o tym samym spotify_id
        givenParsed(track(FIRST), track(FIRST));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        givenCatalogReferences();

        // when
        IngestReport report = service.ingestFile(csv());

        // then
        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.alreadyExisted()).isEqualTo(1);
    }

    @Test
    @DisplayName("utwór obecny w bibliotece nie jest importowany drugi raz")
    void ingestFile_whenTrackAlreadyInLibrary_skipsIt() {

        // given
        givenParsed(track(FIRST), track(SECOND));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of(FIRST));
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        givenCatalogReferences();

        // when
        IngestReport report = service.ingestFile(csv());

        // then
        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.alreadyExisted()).isEqualTo(1);
        verify(trackCatalogRepository).saveAll(skeletonsCaptor.capture());
        assertThat(skeletonsCaptor.getValue())
            .extracting(TrackCatalog::getSpotifyId)
            .containsExactly(SECOND);
    }

    @Test
    @DisplayName("utwór obecny w katalogu nie dostaje drugiego szkieletu, ale wchodzi do biblioteki")
    void ingestFile_whenTrackInCatalogButNotInLibrary_skipsSkeletonAndAddsEntry() {

        // given — katalog zna utwór z importu innego DJ-a; jego dane zostają
        givenParsed(track(FIRST));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of(FIRST));
        givenCatalogReferences();

        // when
        IngestReport report = service.ingestFile(csv());

        // then
        assertThat(report.imported()).isEqualTo(1);
        verify(trackCatalogRepository).saveAll(skeletonsCaptor.capture());
        assertThat(skeletonsCaptor.getValue()).isEmpty();
        verify(libraryEntryRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("szkielet bierze tytuł, artystę i album z pliku")
    void ingestFile_buildsSkeletonFromCsvColumns() {

        // given
        givenParsed(new ParsedTrack(FIRST, "Vivir Mi Vida", "Marc Anthony", "3.0"));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        givenCatalogReferences();

        // when
        service.ingestFile(csv());

        // then
        verify(trackCatalogRepository).saveAll(skeletonsCaptor.capture());
        TrackCatalog skeleton = skeletonsCaptor.getValue().get(0);
        assertThat(skeleton.getTitle()).isEqualTo("Vivir Mi Vida");
        assertThat(skeleton.getArtist()).isEqualTo("Marc Anthony");
        assertThat(skeleton.getAlbum()).isEqualTo("3.0");
    }

    @Test
    @DisplayName("wpisy biblioteczne z importu mają źródło FILE")
    void ingestFile_marksEntriesAsFileSource() {

        // given
        givenParsed(track(FIRST));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        givenCatalogReferences();

        // when
        service.ingestFile(csv());

        // then
        verify(libraryEntryRepository).saveAll(entriesCaptor.capture());
        assertThat(entriesCaptor.getValue().get(0).getSource()).isEqualTo(LibrarySource.FILE);
    }

    @Test
    @DisplayName("błędy wierszy z parsera trafiają do raportu bez zmian")
    void ingestFile_passesRowErrorsToReport() {

        // given
        RowError error = new RowError(3, "brak spotify_id");
        given(parser.parse(any())).willReturn(new CsvParseResult(List.of(track(FIRST)), List.of(error)));
        given(libraryEntryRepository.findExistingTrackIds(anySet())).willReturn(Set.of());
        given(trackCatalogRepository.findExistingIds(anySet())).willReturn(Set.of());
        givenCatalogReferences();

        // when
        IngestReport report = service.ingestFile(csv());

        // then
        assertThat(report.failed()).containsExactly(error);
    }

    @Test
    @DisplayName("pusty plik nie odpytuje bazy o istniejące identyfikatory")
    void ingestFile_whenNoTracksParsed_skipsLookups() {

        // given
        given(parser.parse(any())).willReturn(new CsvParseResult(List.of(), List.of()));

        // when
        IngestReport report = service.ingestFile(csv());

        // then
        assertThat(report.imported()).isZero();
        assertThat(report.alreadyExisted()).isZero();
        verify(libraryEntryRepository, never()).findExistingTrackIds(anySet());
        verify(trackCatalogRepository, never()).findExistingIds(anySet());
    }

    /** Referencja leniwa JPA — bez niej wpis biblioteczny dostaje null zamiast utworu. */
    private void givenCatalogReferences() {

        given(trackCatalogRepository.getReferenceById(any()))
            .willAnswer(call -> new TrackCatalog(call.getArgument(0), "Tytuł", "Artysta"));
    }

    private void givenParsed(ParsedTrack... tracks) {

        given(parser.parse(any())).willReturn(new CsvParseResult(List.of(tracks), List.of()));
    }

    private ParsedTrack track(String spotifyId) {

        return new ParsedTrack(spotifyId, "Tytuł", "Artysta", "Album");
    }

    private InputStream csv() {

        return new ByteArrayInputStream("spotify_id\n".getBytes(StandardCharsets.UTF_8));
    }
}
