package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;
import ru.practicum.mapper.EndpointHitMapper;
import ru.practicum.model.EndpointHit;
import ru.practicum.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsRepository repository;

    public void saveHit(EndpointHitDto dto) {
        // используй EndpointHitMapper
        // используй repository.save()
        EndpointHit entity = EndpointHitMapper.toEntity(dto);
        repository.save(entity);

    }

    public List<ViewStatsDto> getStats(
            LocalDateTime start,
            LocalDateTime end,
            List<String> uris,
            boolean unique) {
        // выбери нужный метод Repository
        // логику выбора ты уже знаешь
        if (uris == null || uris.isEmpty()) {
            if (unique) {
                return repository.findUniqueStats(start, end);
            }
            else {
                return  repository.findStats(start, end);
            }
        }
        else {
            if (unique) {
                return repository.findUniqueStatsByUris(start, end, uris);
            }
            else {
                return repository.findStatsByUris(start, end, uris);
            }
        }
    }
}