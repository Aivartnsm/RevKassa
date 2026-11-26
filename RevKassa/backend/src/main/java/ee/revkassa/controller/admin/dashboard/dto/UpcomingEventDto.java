package ee.revkassa.controller.admin.dashboard.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class UpcomingEventDto implements Serializable {
    private Long eventId;
    private String name;
    private OffsetDateTime startsAt;
    private String location;
    // Active participants only
    private long participantCount;
}
