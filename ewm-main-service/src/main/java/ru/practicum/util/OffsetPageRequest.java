package ru.practicum.util;

import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns the API's "from/size" (offset/limit) parameters into a Spring Data page.
 */
@UtilityClass
public class OffsetPageRequest {
    public static Pageable of(int from, int size) {
        return of(from, size, Sort.by("id"));
    }

    public static Pageable of(int from, int size, Sort sort) {
        return PageRequest.of(from / size, size, sort);
    }
}
