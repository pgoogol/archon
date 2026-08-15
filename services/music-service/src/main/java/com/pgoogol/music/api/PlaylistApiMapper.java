package com.pgoogol.music.api;

import com.pgoogol.music.playlist.PlannedTrack;
import com.pgoogol.music.playlist.PlaylistExport;
import com.pgoogol.music.playlist.PlaylistPlan;
import com.pgoogol.music.playlist.PlaylistSummary;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = CatalogApiMapper.class)
public interface PlaylistApiMapper {

    PlaylistExportResponse toResponse(PlaylistExport export);

    PlaylistSummaryResponse toResponse(PlaylistSummary summary);

    @Mapping(target = "id", source = "playlist.id")
    @Mapping(target = "name", source = "playlist.name")
    @Mapping(target = "spotifyPlaylistId", source = "playlist.spotifyPlaylistId")
    @Mapping(target = "createdAt", source = "playlist.createdAt")
    @Mapping(target = "version", source = "playlist.version")
    @Mapping(target = "tracks", source = "tracks")
    PlaylistResponse toResponse(PlaylistPlan plan);

    PlaylistResponse.PlaylistTrackResponse toResponse(PlannedTrack plannedTrack);
}
