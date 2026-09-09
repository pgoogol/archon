package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.dictionary.domain.Unit;
import com.pgoogol.kitchen.dictionary.domain.UnitKind;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Jednostka miary ze słownika")
public record UnitResponse(
    Long id,
    @Schema(example = "lyzka") String code,
    @Schema(example = "łyżka") String name,
    UnitKind kind) {

    static UnitResponse of(Unit unit) {

        return new UnitResponse(unit.getId(), unit.getCode(), unit.getName(), unit.getKind());
    }
}
