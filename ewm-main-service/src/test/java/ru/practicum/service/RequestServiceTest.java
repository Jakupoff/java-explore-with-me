package ru.practicum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.Request;
import ru.practicum.model.RequestStatus;
import ru.practicum.model.User;
import ru.practicum.repository.RequestRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {
    private static final long INITIATOR_ID = 1L;
    private static final long REQUESTER_ID = 2L;
    private static final long EVENT_ID = 10L;

    @Mock
    private RequestRepository requestRepository;
    @Mock
    private UserService userService;
    @Mock
    private EventService eventService;

    @InjectMocks
    private RequestService requestService;

    private User initiator;
    private User requester;
    private Event event;

    @BeforeEach
    void setUp() {
        initiator = new User(INITIATOR_ID, "Initiator", "init@mail.com");
        requester = new User(REQUESTER_ID, "Requester", "req@mail.com");
        event = new Event();
        event.setId(EVENT_ID);
        event.setInitiator(initiator);
        event.setState(EventState.PUBLISHED);
        event.setParticipantLimit(0);
        event.setRequestModeration(true);
    }

    @Test
    void initiatorCannotJoinOwnEvent() {
        when(userService.getUserOrThrow(INITIATOR_ID)).thenReturn(initiator);
        when(eventService.getEventOrThrow(EVENT_ID)).thenReturn(event);

        assertThrows(ConflictException.class, () -> requestService.create(INITIATOR_ID, EVENT_ID));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void cannotJoinUnpublishedEvent() {
        event.setState(EventState.PENDING);
        when(userService.getUserOrThrow(REQUESTER_ID)).thenReturn(requester);
        when(eventService.getEventOrThrow(EVENT_ID)).thenReturn(event);

        assertThrows(ConflictException.class, () -> requestService.create(REQUESTER_ID, EVENT_ID));
    }

    @Test
    void cannotJoinFullEvent() {
        event.setParticipantLimit(1);
        when(userService.getUserOrThrow(REQUESTER_ID)).thenReturn(requester);
        when(eventService.getEventOrThrow(EVENT_ID)).thenReturn(event);
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> requestService.create(REQUESTER_ID, EVENT_ID));
    }

    @Test
    void requestIsConfirmedAutomaticallyWhenThereIsNoLimit() {
        when(userService.getUserOrThrow(REQUESTER_ID)).thenReturn(requester);
        when(eventService.getEventOrThrow(EVENT_ID)).thenReturn(event);
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        ParticipationRequestDto dto = requestService.create(REQUESTER_ID, EVENT_ID);

        assertEquals(RequestStatus.CONFIRMED, dto.getStatus());
    }

    @Test
    void requestWaitsForModerationWhenEventHasLimit() {
        event.setParticipantLimit(5);
        when(userService.getUserOrThrow(REQUESTER_ID)).thenReturn(requester);
        when(eventService.getEventOrThrow(EVENT_ID)).thenReturn(event);
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        ParticipationRequestDto dto = requestService.create(REQUESTER_ID, EVENT_ID);

        assertEquals(RequestStatus.PENDING, dto.getStatus());
    }

    @Test
    void confirmingBeyondTheLimitRejectsTheRest() {
        event.setParticipantLimit(1);
        Request first = pendingRequest(100L);
        Request second = pendingRequest(101L);
        when(eventService.getUserEventOrThrow(INITIATOR_ID, EVENT_ID)).thenReturn(event);
        when(requestRepository.findAllById(List.of(100L, 101L))).thenReturn(List.of(first, second));
        when(requestRepository.countByEventIdAndStatus(EVENT_ID, RequestStatus.CONFIRMED)).thenReturn(0L);
        when(requestRepository.findByEventIdAndStatus(EVENT_ID, RequestStatus.PENDING)).thenReturn(List.of());

        EventRequestStatusUpdateResult result = requestService.updateStatuses(INITIATOR_ID, EVENT_ID,
                new EventRequestStatusUpdateRequest(List.of(100L, 101L),
                        EventRequestStatusUpdateRequest.Decision.CONFIRMED));

        assertEquals(1, result.getConfirmedRequests().size());
        assertEquals(1, result.getRejectedRequests().size());
        assertEquals(RequestStatus.CONFIRMED, first.getStatus());
        assertEquals(RequestStatus.REJECTED, second.getStatus());
    }

    @Test
    void onlyPendingRequestsCanBeDecided() {
        Request confirmed = pendingRequest(100L);
        confirmed.setStatus(RequestStatus.CONFIRMED);
        when(eventService.getUserEventOrThrow(INITIATOR_ID, EVENT_ID)).thenReturn(event);
        when(requestRepository.findAllById(List.of(100L))).thenReturn(List.of(confirmed));

        assertThrows(ConflictException.class, () -> requestService.updateStatuses(INITIATOR_ID, EVENT_ID,
                new EventRequestStatusUpdateRequest(List.of(100L), EventRequestStatusUpdateRequest.Decision.REJECTED)));
    }

    private Request pendingRequest(long id) {
        return new Request(id, requester, event, LocalDateTime.now(), RequestStatus.PENDING);
    }
}
