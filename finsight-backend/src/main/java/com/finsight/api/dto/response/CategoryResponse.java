package com.finsight.api.dto.response;

import com.finsight.domain.enums.CategoryKind;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String parentName,
    String iconCode,
    boolean systemDefined,
    CategoryKind kind
) {
    public CategoryResponse(UUID id, String name, String parentName, String iconCode, boolean systemDefined) {
        this(id, name, parentName, iconCode, systemDefined, CategoryKind.ANY);
    }
}
