package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.model.Request;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    // Все заявки конкретного пользователя в чужих событиях
    List<Request> findByRequesterId(Long requesterId);

    // Все заявки на конкретное событие (нужно для создателя события)
    List<Request> findByEventId(Long eventId);

    // Проверка, подавал ли уже пользователь заявку (для валидации)
    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);
}