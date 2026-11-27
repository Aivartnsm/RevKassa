package ee.revkassa.controller.event.dto;

import ee.revkassa.persistence.event.EventStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * An event as one member sees it: no other participants' names or fees.
 */
@Data
public class MemberEventDto implements Serializable {
    private Long eventId;
    private String name;
    private OffsetDateTime startsAt;
    private String location;
    private String description;
    // PUBLISHED or CLOSED; members never see drafts or cancelled events
    private EventStatusEnum status;
    private Boolean signupOpen;
    // Active participants only
    private long participantCount;
    private boolean signedUp;
    // The fee has gone to the balance
    private boolean charged;
    // Signed up: the fee copied when joining. Otherwise: the member's level's fee today
    private long feeCents;
    // Not signed up: the next price period that changes the member's fee; null when none (or signed up)
    private NextFeeDto nextFee;
}
