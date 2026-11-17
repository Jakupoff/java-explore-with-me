package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.Request;
import ru.practicum.model.RequestStatus;
import ru.practicum.model.User;
import ru.practicum.repository.RequestRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {
    private final RequestRepository requestRepository;
    private final UserService userService;
    private final EventService eventService;

    // ---------- requests made by a user ----------

    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        userService.getUserOrThrow(userId);
        return RequestMapper.toDtoList(requestRepository.findByRequesterId(userId));
    }

    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId) {
        User requester = userService.getUserOrThrow(userId);
        Event event = eventService.getEventOrThrow(eventId);

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("The initiator cannot request to join their own event");
        }
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot join an unpublished event");
        }
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictException("The request has already been sent");
        }
        int limit = event.getParticipantLimit();
        if (limit > 0 && requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED) >= limit) {
            throw new ConflictException("The participant limit has been reached");
        }

        boolean autoConfirm = limit == 0 || !event.getRequestModeration();
        Request request = Request.builder()
                .requester(requester)
                .event(event)
                .created(LocalDateTime.now())
                .status(autoConfirm ? RequestStatus.CONFIRMED : RequestStatus.PENDING)
                .build();
        return RequestMapper.toDto(requestRepository.save(request));
    }

    @Transactional
    public ParticipationRequestDto cancel(Long userId, Long requestId) {
        userService.getUserOrThrow(userId);
        Request request = requestRepository.findById(requestId)
                .filter(r -> r.getRequester().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " was not found"));
        request.setStatus(RequestStatus.CANCELED);
        return RequestMapper.toDto(request);
    }

    // ---------- requests to an event, seen by its initiator ----------

    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        eventService.getUserEventOrThrow(userId, eventId);
        return RequestMapper.toDtoList(requestRepository.findByEventId(eventId));
    }

    @Transactional
    public EventRequestStatusUpdateResult updateStatuses(Long userId, Long eventId,
                                                         EventRequestStatusUpdateRequest update) {
        Event event = eventService.getUserEventOrThrow(userId, eventId);
        List<Request> requests = requestRepository.findAllById(update.getRequestIds());
        if (requests.size() != update.getRequestIds().size()) {
            throw new NotFoundException("Some of the requests were not found");
        }
        for (Request request : requests) {
            if (!request.getEvent().getId().equals(eventId)) {
                throw new ConflictException("Request with id=" + request.getId() + " belongs to another event");
            }
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
        }

        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();
        if (update.getStatus() == EventRequestStatusUpdateRequest.Decision.REJECTED) {
            requests.forEach(r -> r.setStatus(RequestStatus.REJECTED));
            result.setRejectedRequests(RequestMapper.toDtoList(requests));
            return result;
        }

        int limit = event.getParticipantLimit();
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (limit > 0 && confirmed >= limit) {
            throw new ConflictException("The participant limit has been reached");
        }
        for (Request request : requests) {
            if (limit == 0 || confirmed < limit) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmed++;
                result.getConfirmedRequests().add(RequestMapper.toDto(request));
            } else {
                request.setStatus(RequestStatus.REJECTED);
                result.getRejectedRequests().add(RequestMapper.toDto(request));
            }
        }
        // Once the event is full, every request still waiting is rejected.
        if (limit > 0 && confirmed >= limit) {
            for (Request pending : requestRepository.findByEventIdAndStatus(eventId, RequestStatus.PENDING)) {
                pending.setStatus(RequestStatus.REJECTED);
            }
        }
        return result;
    }
}
