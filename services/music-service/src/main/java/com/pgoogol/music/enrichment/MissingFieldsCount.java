package com.pgoogol.music.enrichment;

/**
 * Liczba utworów z brakami per grupa pól — zasila missing-count w API.
 */
public record MissingFieldsCount(long metadata, long audio, long ai) {

}
