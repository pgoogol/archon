package com.pgoogol.music.api;

import com.pgoogol.music.enrichment.spotify.SpotifyAccountStatus;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SpotifyAccountApiMapper {

    SpotifyAccountResponse toResponse(SpotifyAccountStatus status);
}
