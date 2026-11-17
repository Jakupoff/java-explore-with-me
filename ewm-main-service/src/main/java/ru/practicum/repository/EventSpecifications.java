package ru.practicum.repository;

import jakarta.persistence.criteria.Subquery;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.Request;
import ru.practicum.model.RequestStatus;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Reusable filters for event searches. Each returns null when its filter is not set,
 * which Spring Data treats as "no restriction".
 */
@UtilityClass
public class EventSpecifications {

    public static Specification<Event> initiatorIn(Collection<Long> userIds) {
        return (root, query, cb) -> isEmpty(userIds) ? null : root.get("initiator").get("id").in(userIds);
    }

    public static Specification<Event> stateIn(Collection<EventState> states) {
        return (root, query, cb) -> isEmpty(states) ? null : root.get("state").in(states);
    }

    public static Specification<Event> categoryIn(Collection<Long> categoryIds) {
        return (root, query, cb) -> isEmpty(categoryIds) ? null : root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Event> dateFrom(LocalDateTime start) {
        return (root, query, cb) -> start == null ? null : cb.greaterThanOrEqualTo(root.get("eventDate"), start);
    }

    public static Specification<Event> dateTo(LocalDateTime end) {
        return (root, query, cb) -> end == null ? null : cb.lessThanOrEqualTo(root.get("eventDate"), end);
    }

    public static Specification<Event> paidIs(Boolean paid) {
        return (root, query, cb) -> paid == null ? null : cb.equal(root.get("paid"), paid);
    }

    /**
     * Case-insensitive search in annotation and description.
     */
    public static Specification<Event> textContains(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String pattern = "%" + text.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("annotation")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern));
        };
    }

    /**
     * Events that still accept participants: no limit, or fewer confirmed requests than the limit.
     */
    public static Specification<Event> hasFreeSeats(boolean onlyAvailable) {
        return (root, query, cb) -> {
            if (!onlyAvailable) {
                return null;
            }
            Subquery<Long> confirmed = query.subquery(Long.class);
            var request = confirmed.from(Request.class);
            confirmed.select(cb.count(request))
                    .where(cb.equal(request.get("event"), root),
                            cb.equal(request.get("status"), RequestStatus.CONFIRMED));
            return cb.or(
                    cb.equal(root.get("participantLimit"), 0),
                    cb.greaterThan(root.get("participantLimit"), confirmed));
        };
    }

    private static boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }
}
