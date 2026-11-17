package ru.practicum.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.event.AdminEventSearch;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.event.EventSort;
import ru.practicum.dto.event.NewEventDto;
import ru.practicum.dto.event.PublicEventSearch;
import ru.practicum.dto.event.UpdateEventAdminRequest;
import ru.practicum.dto.event.UpdateEventRequest;
import ru.practicum.dto.event.UpdateEventUserRequest;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.User;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.EventSpecifications;
import ru.practicum.util.OffsetPageRequest;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {
    /** An initiator may schedule or move an event no sooner than this many hours ahead. */
    static final long USER_MIN_HOURS_BEFORE_EVENT = 2;
    /** An admin may publish or move an event no sooner than this many hours ahead. */
    static final long ADMIN_MIN_HOURS_BEFORE_EVENT = 1;

    private final EventRepository eventRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final EventMapper eventMapper;
    private final EventStatsService eventStatsService;

    // ---------- private API: the event's initiator ----------

    @Transactional
    public EventFullDto create(Long userId, NewEventDto dto) {
        User initiator = userService.getUserOrThrow(userId);
        requireNotSoonerThan(dto.getEventDate(), USER_MIN_HOURS_BEFORE_EVENT);
        Event event = eventMapper.toEntity(dto, categoryService.getCategoryOrThrow(dto.getCategory()), initiator);
        return eventMapper.toFullDto(eventRepository.save(event), 0, 0);
    }

    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        userService.getUserOrThrow(userId);
        List<Event> events = eventRepository.findByInitiatorId(userId, OffsetPageRequest.of(from, size));
        return eventStatsService.toShortDtos(events);
    }

    public EventFullDto getUserEvent(Long userId, Long eventId) {
        return eventStatsService.toFullDto(getUserEventOrThrow(userId, eventId));
    }

    @Transactional
    public EventFullDto updateByUser(Long userId, Long eventId, UpdateEventUserRequest request) {
        Event event = getUserEventOrThrow(userId, eventId);
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
        if (request.getEventDate() != null) {
            requireNotSoonerThan(request.getEventDate(), USER_MIN_HOURS_BEFORE_EVENT);
        }
        applyChanges(event, request);
        if (request.getStateAction() != null) {
            switch (request.getStateAction()) {
                case SEND_TO_REVIEW -> event.setState(EventState.PENDING);
                case CANCEL_REVIEW -> event.setState(EventState.CANCELED);
                default -> throw new BadRequestException("Unknown state action");
            }
        }
        return eventStatsService.toFullDto(event);
    }

    // ---------- admin API ----------

    public List<EventFullDto> search(AdminEventSearch search) {
        requireValidRange(search.rangeStart(), search.rangeEnd());
        Specification<Event> spec = Specification.where(EventSpecifications.initiatorIn(search.users()))
                .and(EventSpecifications.stateIn(search.states()))
                .and(EventSpecifications.categoryIn(search.categories()))
                .and(EventSpecifications.dateFrom(search.rangeStart()))
                .and(EventSpecifications.dateTo(search.rangeEnd()));
        List<Event> events = eventRepository.findAll(spec, OffsetPageRequest.of(search.from(), search.size()))
                .getContent();
        return eventStatsService.toFullDtos(events);
    }

    @Transactional
    public EventFullDto updateByAdmin(Long eventId, UpdateEventAdminRequest request) {
        Event event = getEventOrThrow(eventId);
        if (request.getEventDate() != null) {
            requireNotSoonerThan(request.getEventDate(), ADMIN_MIN_HOURS_BEFORE_EVENT);
        }
        if (request.getStateAction() != null) {
            switch (request.getStateAction()) {
                case PUBLISH_EVENT -> publish(event, request);
                case REJECT_EVENT -> reject(event);
                default -> throw new BadRequestException("Unknown state action");
            }
        }
        applyChanges(event, request);
        return eventStatsService.toFullDto(event);
    }

    private void publish(Event event, UpdateEventAdminRequest request) {
        if (event.getState() != EventState.PENDING) {
            throw new ConflictException("Cannot publish the event because it's not in the right state: "
                    + event.getState());
        }
        LocalDateTime eventDate = request.getEventDate() != null ? request.getEventDate() : event.getEventDate();
        if (eventDate.isBefore(LocalDateTime.now().plusHours(ADMIN_MIN_HOURS_BEFORE_EVENT))) {
            throw new ConflictException("Cannot publish an event that starts in less than an hour");
        }
        event.setState(EventState.PUBLISHED);
        event.setPublishedOn(LocalDateTime.now());
    }

    private void reject(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Cannot reject the event because it's already published");
        }
        event.setState(EventState.CANCELED);
    }

    // ---------- public API ----------

    public List<EventShortDto> searchPublished(PublicEventSearch search, HttpServletRequest httpRequest) {
        requireValidRange(search.rangeStart(), search.rangeEnd());
        LocalDateTime start = search.rangeStart();
        if (start == null && search.rangeEnd() == null) {
            start = LocalDateTime.now();
        }
        Specification<Event> spec = Specification.where(EventSpecifications.stateIn(List.of(EventState.PUBLISHED)))
                .and(EventSpecifications.textContains(search.text()))
                .and(EventSpecifications.categoryIn(search.categories()))
                .and(EventSpecifications.paidIs(search.paid()))
                .and(EventSpecifications.dateFrom(start))
                .and(EventSpecifications.dateTo(search.rangeEnd()))
                .and(EventSpecifications.hasFreeSeats(search.onlyAvailable()));

        Sort sort = search.sort() == EventSort.EVENT_DATE ? Sort.by("eventDate") : Sort.by("id");
        List<Event> events = eventRepository.findAll(spec, OffsetPageRequest.of(search.from(), search.size(), sort))
                .getContent();

        eventStatsService.saveHit(httpRequest);
        List<EventShortDto> result = eventStatsService.toShortDtos(events);
        if (search.sort() == EventSort.VIEWS) {
            result = result.stream().sorted(Comparator.comparing(EventShortDto::getViews).reversed()).toList();
        }
        return result;
    }

    public EventFullDto getPublished(Long eventId, HttpServletRequest httpRequest) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> e.getState() == EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        // Record the visit first, so the response already counts it.
        eventStatsService.saveHit(httpRequest);
        return eventStatsService.toFullDto(event);
    }

    // ---------- shared helpers ----------

    public Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    public Event getUserEventOrThrow(Long userId, Long eventId) {
        userService.getUserOrThrow(userId);
        return eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private void applyChanges(Event event, UpdateEventRequest request) {
        if (request.getAnnotation() != null) {
            event.setAnnotation(request.getAnnotation());
        }
        if (request.getCategory() != null) {
            event.setCategory(categoryService.getCategoryOrThrow(request.getCategory()));
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
        }
        if (request.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(request.getLocation()));
        }
        if (request.getPaid() != null) {
            event.setPaid(request.getPaid());
        }
        if (request.getParticipantLimit() != null) {
            event.setParticipantLimit(request.getParticipantLimit());
        }
        if (request.getRequestModeration() != null) {
            event.setRequestModeration(request.getRequestModeration());
        }
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
    }

    private void requireNotSoonerThan(LocalDateTime eventDate, long hours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new BadRequestException("Event date must be at least " + hours + " hour(s) from now: " + eventDate);
        }
    }

    private void requireValidRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new BadRequestException("rangeStart must not be after rangeEnd");
        }
    }
}
