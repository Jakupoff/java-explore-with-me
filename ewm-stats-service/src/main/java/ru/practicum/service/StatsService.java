package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;
import ru.practicum.exception.BadRequestException;
import ru.practicum.mapper.EndpointHitMapper;
import ru.practicum.model.EndpointHit;
import ru.practicum.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository repository;

    @Transactional
    public EndpointHitDto saveHit(EndpointHitDto dto) {
        EndpointHit saved = repository.save(EndpointHitMapper.toEntity(dto));
        return EndpointHitMapper.toDto(saved);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        if (start.isAfter(end)) {
            throw new BadRequestException("start must not be after end");
        }
        boolean allUris = uris == null || uris.isEmpty();
        if (unique) {
            return allUris ? repository.findUniqueStats(start, end) : repository.findUniqueStatsByUris(start, end, uris);
        }
        return allUris ? repository.findStats(start, end) : repository.findStatsByUris(start, end, uris);
    }
}
