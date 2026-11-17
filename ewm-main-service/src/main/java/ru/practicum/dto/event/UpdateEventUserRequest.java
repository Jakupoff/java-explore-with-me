package ru.practicum.dto.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEventUserRequest extends UpdateEventRequest {
    private UserStateAction stateAction;

    public enum UserStateAction {
        SEND_TO_REVIEW,
        CANCEL_REVIEW
    }
}
