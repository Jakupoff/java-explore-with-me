package ru.practicum.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * HTTP client for the statistics service.
 * Failures are logged and swallowed: the main service must keep working if statistics are down.
 */
@Slf4j
@Component
public class StatsClient {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;

    public StatsClient(RestClient.Builder builder,
                       @Value("${stats-server.url:http://localhost:9090}") String serverUrl) {
        this.restClient = builder.baseUrl(serverUrl).build();
    }

    public void hit(EndpointHitDto hit) {
        try {
            restClient.post()
                    .uri("/hit")
                    .body(hit)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not send hit to stats service: {}", e.getMessage());
        }
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       Collection<String> uris, boolean unique) {
        try {
            List<ViewStatsDto> stats = restClient.get()
                    .uri(builder -> buildStatsUri(builder, start, end, uris, unique))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return stats == null ? List.of() : stats;
        } catch (RestClientException e) {
            log.warn("Could not load stats: {}", e.getMessage());
            return List.of();
        }
    }

    private URI buildStatsUri(UriBuilder builder, LocalDateTime start, LocalDateTime end,
                              Collection<String> uris, boolean unique) {
        builder.path("/stats")
                .queryParam("start", start.format(FORMATTER))
                .queryParam("end", end.format(FORMATTER))
                .queryParam("unique", unique);
        if (uris != null && !uris.isEmpty()) {
            builder.queryParam("uris", uris.toArray());
        }
        return builder.build();
    }
}
