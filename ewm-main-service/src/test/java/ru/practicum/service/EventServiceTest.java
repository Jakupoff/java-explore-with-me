package ru.practicum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.dto.event.LocationDto;
import ru.practicum.dto.event.NewEventDto;
import ru.practicum.dto.event.UpdateEventAdminRequest;
import ru.practicum.dto.event.UpdateEventUserRequest;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.User;
import ru.practicum.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {
    private static final long USER_ID = 1L;
    private static final long EVENT_ID = 10L;

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserService userService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private EventStatsService eventStatsService;

    @InjectMocks
    private EventService eventService;

    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(EVENT_ID);
        event.setState(EventState.PENDING);
        event.setEventDate(LocalDateTime.now().plusDays(3));
    }

    @Test
    void cannotCreateEventStartingInLessThanTwoHours() {
        when(userService.getUserOrThrow(USER_ID)).thenReturn(new User(USER_ID, "User", "user@mail.com"));
        NewEventDto dto = new NewEventDto("A long enough annotation text", 1L, "A long enough description text",
                LocalDateTime.now().plusHours(1), new LocationDto(1f, 2f), false, 0, true, "Title");

        assertThrows(BadRequestException.class, () -> eventService.create(USER_ID, dto));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void initiatorCannotChangePublishedEvent() {
        event.setState(EventState.PUBLISHED);
        when(eventRepository.findByIdAndInitiatorId(EVENT_ID, USER_ID)).thenReturn(Optional.of(event));

        assertThrows(ConflictException.class,
                () -> eventService.updateByUser(USER_ID, EVENT_ID, new UpdateEventUserRequest()));
    }

    @Test
    void adminPublishesPendingEvent() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        UpdateEventAdminRequest request = new UpdateEventAdminRequest();
        request.setStateAction(UpdateEventAdminRequest.AdminStateAction.PUBLISH_EVENT);

        eventService.updateByAdmin(EVENT_ID, request);

        assertEquals(EventState.PUBLISHED, event.getState());
        assertNotNull(event.getPublishedOn());
    }

    @Test
    void adminCannotPublishCanceledEvent() {
        event.setState(EventState.CANCELED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        UpdateEventAdminRequest request = new UpdateEventAdminRequest();
        request.setStateAction(UpdateEventAdminRequest.AdminStateAction.PUBLISH_EVENT);

        assertThrows(ConflictException.class, () -> eventService.updateByAdmin(EVENT_ID, request));
    }

    @Test
    void adminCannotRejectPublishedEvent() {
        event.setState(EventState.PUBLISHED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        UpdateEventAdminRequest request = new UpdateEventAdminRequest();
        request.setStateAction(UpdateEventAdminRequest.AdminStateAction.REJECT_EVENT);

        assertThrows(ConflictException.class, () -> eventService.updateByAdmin(EVENT_ID, request));
    }
}
