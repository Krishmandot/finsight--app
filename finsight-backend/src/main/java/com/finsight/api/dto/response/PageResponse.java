package com.finsight.api.dto.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stable JSON shape for paginated results (Spring's PageImpl is not a stable serialization contract). */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return of(page.map(mapper::apply));
    }
}
