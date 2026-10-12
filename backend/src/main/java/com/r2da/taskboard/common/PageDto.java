package com.r2da.taskboard.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageDto<T>(List<T> items, int page, int size, long total) {

    public static final int MAX_SIZE = 200;

    public static Pageable request(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 1) - 1, Math.clamp(size, 1, MAX_SIZE), sort);
    }

    public static <E, T> PageDto<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
        return new PageDto<>(mapper.apply(page.getContent()), page.getNumber() + 1, page.getSize(),
                page.getTotalElements());
    }
}
