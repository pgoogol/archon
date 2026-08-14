package com.pgoogol.music.library;

import com.pgoogol.music.TestcontainersConfiguration;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LibraryEntryRepositoryTest {

    @Autowired
    private LibraryEntryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByTrackSpotifyId_whenEntryWithPrivateDataSaved_readsAllFieldsBack() {

        // given
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");
        entityManager.persist(track);
        LibraryEntry entry = new LibraryEntry(track, LibrarySource.FILE);
        entry.setDjNotes("mocny opener na wesele");
        entry.setCustomTags(List.of("opener", "wesele"));
        entry.setDjSlotOverride("peak");
        entry.setRating(5);
        repository.saveAndFlush(entry);
        entityManager.clear();

        // when
        Optional<LibraryEntry> found = repository.findByTrackSpotifyId("sp-1");

        // then
        assertThat(found).hasValueSatisfying(e -> {
            assertThat(e.getSource()).isEqualTo(LibrarySource.FILE);
            assertThat(e.getAddedAt()).isNotNull();
            assertThat(e.getDjNotes()).isEqualTo("mocny opener na wesele");
            assertThat(e.getCustomTags()).containsExactly("opener", "wesele");
            assertThat(e.getDjSlotOverride()).isEqualTo("peak");
            assertThat(e.getRating()).isEqualTo(5);
        });
    }

    @Test
    void save_whenSecondEntryForSameTrack_throwsDataIntegrityViolationException() {

        // given
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");
        entityManager.persist(track);
        repository.saveAndFlush(new LibraryEntry(track, LibrarySource.FILE));

        // when
        Throwable thrown = catchThrowable(
            () -> repository.saveAndFlush(new LibraryEntry(track, LibrarySource.PLAYLIST)));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByTrackSpotifyId_whenNoEntry_returnsFalse() {

        // given
        entityManager.persist(TrackCatalogFixtures.skeletonTrack("sp-1"));

        // when
        boolean exists = repository.existsByTrackSpotifyId("sp-1");

        // then
        assertThat(exists).isFalse();
    }
}
