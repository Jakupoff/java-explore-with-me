package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.ViewStatsDto;
import ru.practicum.exception.BadRequestException;
import ru.practicum.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {
    private static final LocalDateTime START = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2025, 1, 1, 0, 0);

    @Mock
    private StatsRepository repository;

    @InjectMocks
    private StatsService service;

    @Test
    void rejectsStartAfterEnd() {
        assertThrows(BadRequestException.class, () -> service.getStats(END, START, null, false));
        verifyNoInteractions(repository);
    }

    @Test
    void usesUniqueQueryWithUris() {
        List<String> uris = List.of("/events/1");
        List<ViewStatsDto> expected = List.of(new ViewStatsDto("ewm-main-service", "/events/1", 3L));
        when(repository.findUniqueStatsByUris(START, END, uris)).thenReturn(expected);

        assertEquals(expected, service.getStats(START, END, uris, true));
    }

    @Test
    void usesPlainQueryWithoutUris() {
        service.getStats(START, END, List.of(), false);

        verify(repository).findStats(START, END);
    }
}
