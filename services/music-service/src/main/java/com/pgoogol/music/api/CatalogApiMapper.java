package com.pgoogol.music.api;

import com.pgoogol.music.catalog.CamelotKey;
import com.pgoogol.music.catalog.ManualMetrics;
import com.pgoogol.music.catalog.TrackCatalog;
import com.pgoogol.music.library.LibraryEntry;
import com.pgoogol.music.library.LibraryRow;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Encja → DTO kontraktu. Implementację generuje MapStruct przy kompilacji.
 *
 * <p>Niezmapowane pole docelowe zatrzymuje build (polityka {@code ERROR} ustawiona
 * w root POM), więc kolumna dołożona do kontraktu nie przejdzie niezauważona
 * jako {@code null} — a to była jedyna realna wada ręcznego mapowania po
 * pozycjach argumentów.</p>
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CatalogApiMapper {

    TrackMetricsResponse toResponse(ManualMetrics metrics);

    /** Wiersz ekranu Biblioteka — katalog plus dane DJ-a, o ile utwór jest u niego. */
    @Mapping(target = "library", source = "entry")
    CatalogRowResponse toResponse(LibraryRow row);

    CatalogRowResponse.TrackLibraryResponse toLibraryResponse(LibraryEntry entry);

    /** {@code camelot} nie jest kolumną — liczy się z tonacji przy mapowaniu. */
    @Mapping(target = "camelot", source = "musicalKey", qualifiedByName = "camelotLabel")
    TrackResponse toResponse(TrackCatalog track);

    @Named("camelotLabel")
    static String camelotLabel(String musicalKey) {

        return CamelotKey.ofMusicalKey(musicalKey).map(CamelotKey::label).orElse(null);
    }
}
