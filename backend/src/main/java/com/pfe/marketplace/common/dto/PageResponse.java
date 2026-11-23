package com.pfe.marketplace.common.dto;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stable JSON shape for paginated results (Spring's Page serialization is not a stable contract). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)
        implements Serializable {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
