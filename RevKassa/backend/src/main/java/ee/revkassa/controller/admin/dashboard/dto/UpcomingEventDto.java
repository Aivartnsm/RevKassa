package ee.revkassa.controller.admin.dashboard.dto;

import ee.revkassa.persistence.event.EventStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class UpcomingEventDto implements Serializable {
    private Long eventId;
    private String name;
    private OffsetDateTime startsAt;
    private String location;
    // DRAFT (not visible to members yet) or PUBLISHED
    private EventStatusEnum status;
    // Active participants only
    private long participantCount;
}
