package com.pgoogol.music.api;

import com.pgoogol.music.common.ValidationException;
import com.pgoogol.music.library.LibraryEntry;
import com.pgoogol.music.library.LibraryEntryUpdate;
import com.pgoogol.music.library.LibraryOverview;
import com.pgoogol.music.playlist.DjSlot;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = CatalogApiMapper.class)
public interface LibraryApiMapper {

    LibraryOverviewResponse toResponse(LibraryOverview overview);

    LibraryOverviewResponse.ScaleResponse toResponse(LibraryOverview.Scale scale);

    LibraryOverviewResponse.QualityResponse toResponse(LibraryOverview.Quality quality);

    LibraryOverviewResponse.SoundResponse toResponse(LibraryOverview.Sound sound);

    LibraryOverviewResponse.TimelineResponse toResponse(LibraryOverview.Timeline timeline);

    LibraryOverviewResponse.TasteResponse toResponse(LibraryOverview.Taste taste);

    LibraryOverviewResponse.BucketResponse toResponse(LibraryOverview.Bucket bucket);

    LibraryOverviewResponse.MatrixCellResponse toResponse(LibraryOverview.MatrixCell cell);

    LibraryOverviewResponse.MetricResponse toResponse(LibraryOverview.Metric metric);

    LibraryOverviewResponse.RecentTrackResponse toResponse(LibraryOverview.RecentTrack track);

    @Mapping(target = "spotifyId", source = "track.spotifyId")
    @Mapping(target = "track", source = "track")
    LibraryEntryResponse toResponse(LibraryEntry entry);

    @Mapping(target = "djSlotOverride", source = "djSlotOverride",
        qualifiedByName = "canonicalSlotOverride")
    // `version` z żądania to wersja OCZEKIWANA przez klienta — nazwy się różnią,
    // więc mapowanie po pozycji argumentu wiązało je milcząco
    @Mapping(target = "expectedVersion", source = "version")
    LibraryEntryUpdate toUpdate(UpdateLibraryEntryRequest request);

    /** Override slotu musi być jedną z wartości {@link DjSlot}; pusty = wyczyszczenie. */
    @Named("canonicalSlotOverride")
    static String canonicalSlotOverride(String djSlotOverride) {

        if (Objects.isNull(djSlotOverride) || djSlotOverride.isBlank()) {

            return djSlotOverride;
        }
        return DjSlot.parse(djSlotOverride)
            .map(DjSlot::name)
            .orElseThrow(() -> new ValidationException("DJ_SLOT_INVALID",
                "Nieznany slot '%s' — dopuszczalne: %s".formatted(djSlotOverride,
                    Arrays.stream(DjSlot.values()).map(DjSlot::name)
                        .collect(Collectors.joining(", ")))));
    }
}
