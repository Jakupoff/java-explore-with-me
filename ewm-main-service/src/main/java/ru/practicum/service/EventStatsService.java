package ru.practicum.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;
import ru.practicum.client.StatsClient;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Event;
import ru.practicum.model.RequestStatus;
import ru.practicum.repository.EventRequestCount;
import ru.practicum.repository.RequestRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adds the computed numbers (confirmed requests and views) to event DTOs,
 * and records public page visits in the statistics service.
 */
@Service
@RequiredArgsConstructor
public class EventStatsService {
    private static final String EVENTS_URI = "/events/";

    private final StatsClient statsClient;
    private final RequestRepository requestRepository;
    private final EventMapper eventMapper;

    @Value("${app.name:ewm-main-service}")
    private String appName;

    public void saveHit(HttpServletRequest request) {
        statsClient.hit(EndpointHitDto.builder()
                .app(appName)
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build());
    }

    public Map<Long, Long> getConfirmedCounts(Collection<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = events.stream().map(Event::getId).toList();
        return requestRepository.countByEventIdsAndStatus(ids, RequestStatus.CONFIRMED).stream()
                .collect(Collectors.toMap(EventRequestCount::getEventId, EventRequestCount::getCount));
    }

    /**
     * Unique views per event id, taken from the statistics service. Unpublished events have no views.
     */
    public Map<Long, Long> getViews(Collection<Event> events) {
        LocalDateTime start = events.stream()
                .map(Event::getPublishedOn)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        if (start == null) {
            return Map.of();
        }
        List<String> uris = events.stream().map(e -> EVENTS_URI + e.getId()).toList();
        List<ViewStatsDto> stats = statsClient.getStats(start, LocalDateTime.now().plusMinutes(1), uris, true);

        Map<Long, Long> views = new HashMap<>();
        for (ViewStatsDto stat : stats) {
            Long eventId = parseEventId(stat.getUri());
            if (eventId != null) {
                views.merge(eventId, stat.getHits(), Long::sum);
            }
        }
        return views;
    }

    public EventFullDto toFullDto(Event event) {
        return toFullDtos(List.of(event)).getFirst();
    }

    public List<EventFullDto> toFullDtos(List<Event> events) {
        Map<Long, Long> confirmed = getConfirmedCounts(events);
        Map<Long, Long> views = getViews(events);
        return events.stream()
                .map(e -> eventMapper.toFullDto(e, confirmed.getOrDefault(e.getId(), 0L), views.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    public List<EventShortDto> toShortDtos(Collection<Event> events) {
        Map<Long, Long> confirmed = getConfirmedCounts(events);
        Map<Long, Long> views = getViews(events);
        return events.stream()
                .map(e -> eventMapper.toShortDto(e, confirmed.getOrDefault(e.getId(), 0L), views.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    private Long parseEventId(String uri) {
        if (uri == null || !uri.startsWith(EVENTS_URI)) {
            return null;
        }
        try {
            return Long.parseLong(uri.substring(EVENTS_URI.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
