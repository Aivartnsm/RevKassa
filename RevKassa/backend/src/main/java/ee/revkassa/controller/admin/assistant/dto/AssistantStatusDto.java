package ee.revkassa.controller.admin.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantStatusDto implements Serializable {
    // false: no API key set, the UI hides the assistant
    private boolean enabled;
}
