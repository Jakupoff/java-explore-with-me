package ru.practicum.repository;

/**
 * Projection: how many requests of some status an event has.
 */
public interface EventRequestCount {
    Long getEventId();

    Long getCount();
}
