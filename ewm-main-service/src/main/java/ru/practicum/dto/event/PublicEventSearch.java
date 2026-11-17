package ru.practicum.dto.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filters for GET /events. Any filter may be null.
 */
public record PublicEventSearch(
        String text,
        List<Long> categories,
        Boolean paid,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        boolean onlyAvailable,
        EventSort sort,
        int from,
        int size) {
}
