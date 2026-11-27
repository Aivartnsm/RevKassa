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
    // The fees of the active participants, charged or not
    private long feeIncomeCents;
    // Active participants whose fee has not gone to the balance yet, and the sum of those fees
    private long unchargedCount;
    private long unchargedCents;
    // Price periods, the one without a date (from the start) first, then by date. Always at least that one
    private List<EventFeePeriodDto> feePeriods;
    // Active ones by name, then removed ones by name
    private List<EventParticipantDto> participants;
    private EventBudgetDto budget;
}
