package ee.revkassa.controller.admin.event.dto;

import ee.revkassa.persistence.event.EventStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class EventSummaryDto implements Serializable {
    private Long eventId;
    private String name;
    private OffsetDateTime startsAt;
    private String location;
    private EventStatusEnum status;
    private Boolean signupOpen;
    // Active participants only
    private long participantCount;
    // Fees charged to the active participants
    private long feeIncomeCents;
}
