package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.compilation.CompilationDto;
import ru.practicum.dto.compilation.NewCompilationDto;
import ru.practicum.dto.compilation.UpdateCompilationRequest;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.model.Compilation;
import ru.practicum.model.Event;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.util.OffsetPageRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final EventStatsService eventStatsService;

    @Transactional
    public CompilationDto create(NewCompilationDto dto) {
        if (compilationRepository.existsByTitle(dto.getTitle())) {
            throw new ConflictException("Compilation with title=" + dto.getTitle() + " already exists");
        }
        Compilation compilation = Compilation.builder()
                .title(dto.getTitle())
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .events(loadEvents(dto.getEvents()))
                .build();
        return toDto(compilationRepository.save(compilation));
    }

    @Transactional
    public CompilationDto update(Long compId, UpdateCompilationRequest request) {
        Compilation compilation = getCompilationOrThrow(compId);
        if (request.getTitle() != null) {
            if (compilationRepository.existsByTitleAndIdNot(request.getTitle(), compId)) {
                throw new ConflictException("Compilation with title=" + request.getTitle() + " already exists");
            }
            compilation.setTitle(request.getTitle());
        }
        if (request.getPinned() != null) {
            compilation.setPinned(request.getPinned());
        }
        if (request.getEvents() != null) {
            compilation.setEvents(loadEvents(request.getEvents()));
        }
        return toDto(compilation);
    }

    @Transactional
    public void delete(Long compId) {
        compilationRepository.delete(getCompilationOrThrow(compId));
    }

    public List<CompilationDto> getAll(Boolean pinned, int from, int size) {
        Pageable pageable = OffsetPageRequest.of(from, size);
        List<Compilation> compilations = pinned == null
                ? compilationRepository.findAll(pageable).getContent()
                : compilationRepository.findByPinned(pinned, pageable);
        return compilations.stream().map(this::toDto).toList();
    }

    public CompilationDto getById(Long compId) {
        return toDto(getCompilationOrThrow(compId));
    }

    private Compilation getCompilationOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
    }

    private Set<Event> loadEvents(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<Event> events = eventRepository.findAllById(ids);
        if (events.size() != ids.size()) {
            throw new NotFoundException("Some of the events were not found");
        }
        return new HashSet<>(events);
    }

    private CompilationDto toDto(Compilation compilation) {
        return new CompilationDto(
                compilation.getId(),
                eventStatsService.toShortDtos(compilation.getEvents()),
                compilation.getPinned(),
                compilation.getTitle());
    }
}
