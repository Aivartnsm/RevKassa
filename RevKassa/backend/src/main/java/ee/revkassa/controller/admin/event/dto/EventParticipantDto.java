package ee.revkassa.controller.admin.event.dto;

import ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class EventParticipantDto implements Serializable {
    private Long memberId;
    private String displayName;
    // The level copied when the member joined
    private String levelName;
    private Long feeCents;
    private EventParticipantStatusEnum status;
    // The fee has gone to the balance (for a removed participant: it was, and was returned)
    private Boolean charged;
    // Signed up themselves; otherwise added by an admin (addedByName)
    private Boolean selfSignup;
    private String addedByName;
    private OffsetDateTime addedAt;
    private OffsetDateTime removedAt;
}
