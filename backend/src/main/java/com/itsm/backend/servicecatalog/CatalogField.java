package com.itsm.backend.servicecatalog;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One question on a catalog item's form; stored as JSON in {@code catalog_items.fields}.
 *
 * @param key     stable name of the answer, e.g. {@code model}
 * @param options the choices of a SELECT field; empty for other types
 */
public record CatalogField(
        @NotBlank @Pattern(regexp = "[a-z][a-zA-Z0-9]{0,39}", message = "must be a short camelCase name") String key,
        @NotBlank @Size(max = 100) String label,
        @NotNull CatalogFieldType type,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> options,
        boolean required) {

    public CatalogField {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public enum CatalogFieldType {
        TEXT, TEXTAREA, NUMBER, SELECT
    }
}
