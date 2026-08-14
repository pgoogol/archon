package com.pgoogol.music.playlist;

import com.pgoogol.music.TestcontainersConfiguration;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.catalog.TrackCatalogFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class PlaylistTrackRepositoryTest {

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_whenPlaylistCreated_persistsNameAndCreatedAt() {

        // given
        Playlist playlist = new Playlist("Sobota — wesele");

        // when
        playlistRepository.saveAndFlush(playlist);
        Optional<Playlist> found = playlistRepository.findById(playlist.getId());

        // then
        assertThat(found).hasValueSatisfying(p -> {
            assertThat(p.getName()).isEqualTo("Sobota — wesele");
            assertThat(p.getCreatedAt()).isNotNull();
            assertThat(p.getSpotifyPlaylistId()).isNull();
        });
    }

    @Test
    void findAllByPlaylistIdOrderByPositionAsc_whenTracksSavedOutOfOrder_returnsSortedByPosition() {

        // given
        Playlist playlist = playlistRepository.save(new Playlist("Sobota — wesele"));
        TrackCatalog first = TrackCatalogFixtures.skeletonTrack("sp-1");
        TrackCatalog second = TrackCatalogFixtures.skeletonTrack("sp-2");
        TrackCatalog third = TrackCatalogFixtures.skeletonTrack("sp-3");
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.persist(third);
        playlistTrackRepository.save(new PlaylistTrack(playlist, third, 3));
        playlistTrackRepository.save(new PlaylistTrack(playlist, first, 1));
        playlistTrackRepository.save(new PlaylistTrack(playlist, second, 2));

        // when
        List<PlaylistTrack> tracks =
            playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(playlist.getId());

        // then
        assertThat(tracks).extracting(PlaylistTrack::getPosition).containsExactly(1, 2, 3);
    }
}
