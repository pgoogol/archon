package com.pgoogol.finance.api;

import com.pgoogol.finance.category.domain.CategoryDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    Long parentId,

    @NotBlank @Size(max = 100)
    String name,

    @NotNull
    CategoryDirection direction) {

}
