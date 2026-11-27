package ee.revkassa.controller.admin.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * The event's detail after adding, and the members left out (empty when everyone was added).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantsAddResponseDto implements Serializable {
    private EventDto event;
    private List<SkippedMemberDto> skipped;
}
