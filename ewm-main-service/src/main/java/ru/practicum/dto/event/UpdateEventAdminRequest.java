package ru.practicum.dto.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEventAdminRequest extends UpdateEventRequest {
    private AdminStateAction stateAction;

    public enum AdminStateAction {
        PUBLISH_EVENT,
        REJECT_EVENT
    }
}
