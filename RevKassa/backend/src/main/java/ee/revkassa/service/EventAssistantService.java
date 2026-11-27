package ee.revkassa.service;

import ee.revkassa.controller.admin.assistant.dto.AssistantStatusDto;
import ee.revkassa.infrastructure.ai.EventAssistant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The admin's event assistant (B31: whether it is on; B32: asking it about one event).
 */
@Service
@RequiredArgsConstructor
public class EventAssistantService {

    private final EventAssistant eventAssistant;

    public AssistantStatusDto getStatus() {
        return new AssistantStatusDto(eventAssistant.isEnabled());
    }
}
