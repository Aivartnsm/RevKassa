package ee.revkassa.persistence.eventlevelfee;

import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * One level's fee for one event. A level without a row pays 0 on that event, so only fees above 0 are stored.
 */
@Getter
@Setter
@Entity
@Table(name = "event_level_fee")
public class EventLevelFee {

    @EmbeddedId
    private EventLevelFeeId id;

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @MapsId("levelId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private MembershipLevel level;

    @NotNull
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;
}
