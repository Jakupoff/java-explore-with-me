package ru.practicum.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.EndpointHitDto;
import ru.practicum.model.EndpointHit;

@UtilityClass
public class EndpointHitMapper {
    public static EndpointHit toEntity(EndpointHitDto dto) {
        return new EndpointHit(
                null, // id генерируется базой данных автоматически
                dto.getApp(),
                dto.getUri(),
                dto.getIp(),
                dto.getTimestamp()
        );
    }

    public static EndpointHitDto toDto(EndpointHit hit) {
        return new EndpointHitDto(hit.getId(), hit.getApp(), hit.getUri(), hit.getIp(), hit.getTimestamp());
    }
}
