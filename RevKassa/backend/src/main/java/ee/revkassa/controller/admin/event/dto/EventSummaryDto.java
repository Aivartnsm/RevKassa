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
    // The fees of the active participants, charged or not
    private long feeIncomeCents;
    // Fee income minus planned costs, and minus the actual costs filled in so far
    private long plannedResultCents;
    private long actualResultCents;
}
