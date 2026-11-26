package ee.revkassa.controller.admin.event.dto;

import ee.revkassa.persistence.event.EventStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class EventDto implements Serializable {
    private Long eventId;
    private String name;
    private OffsetDateTime startsAt;
    private String location;
    private String description;
    private EventStatusEnum status;
    private Boolean signupOpen;
    // false when CLOSED or CANCELLED: nothing can be changed any more
    private Boolean editable;
    private String createdByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    // Send back with PUT and PUT fees; saving fees also raises it
    private Long version;
    // Active participants only
    private long participantCount;
    // Fees charged to the active participants
    private long feeIncomeCents;
    // Price periods, the one without a date (from the start) first, then by date. Always at least that one
    private List<EventFeePeriodDto> feePeriods;
}
