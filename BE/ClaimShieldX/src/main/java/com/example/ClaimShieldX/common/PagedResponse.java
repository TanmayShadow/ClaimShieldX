package com.example.ClaimShieldX.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** The envelope for every list endpoint (api-contract section 2). */
public record PagedResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
