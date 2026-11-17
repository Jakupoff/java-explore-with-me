package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.Request;
import ru.practicum.model.RequestStatus;

import java.util.Collection;
import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    // Все заявки конкретного пользователя в чужих событиях
    List<Request> findByRequesterId(Long requesterId);

    // Все заявки на конкретное событие (нужно для создателя события)
    List<Request> findByEventId(Long eventId);

    // Проверка, подавал ли уже пользователь заявку (для валидации)
    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    @Query("SELECT r.event.id AS eventId, COUNT(r) AS count " +
            "FROM Request r " +
            "WHERE r.event.id IN :eventIds AND r.status = :status " +
            "GROUP BY r.event.id")
    List<EventRequestCount> countByEventIdsAndStatus(@Param("eventIds") Collection<Long> eventIds,
                                                     @Param("status") RequestStatus status);
}
