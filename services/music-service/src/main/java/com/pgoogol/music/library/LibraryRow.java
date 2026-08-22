package com.pgoogol.music.library;

import com.pgoogol.music.catalog.TrackCatalog;
import org.springframework.lang.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Wiersz ekranu Biblioteka (M5.6): utwór z katalogu i — o ile DJ go ma —
 * jego wpis biblioteczny. Dwa obiekty, nie jeden sklejony rekord: rozdział
 * danych obowiązuje też w odpowiedzi API, a {@code entry} puste znaczy
 * dokładnie „utwór jest w katalogu, ale nie w bibliotece".
 */
public record LibraryRow(TrackCatalog track, @Nullable LibraryEntry entry) {

    public LibraryRow {

        Objects.requireNonNull(track, "track");
    }

    public Optional<LibraryEntry> libraryEntry() {

        return Optional.ofNullable(entry);
    }
}
