package ru.practicum.dto.event;

import ru.practicum.model.EventState;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filters for GET /admin/events. Any filter may be null.
 */
public record AdminEventSearch(
        List<Long> users,
        List<EventState> states,
        List<Long> categories,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        int from,
        int size) {
}
