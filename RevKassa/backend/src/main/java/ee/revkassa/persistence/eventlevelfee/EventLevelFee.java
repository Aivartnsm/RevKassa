package ee.revkassa.persistence.eventlevelfee;

import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One level's fee in one price period of an event. A participant pays their level's amount in the period that holds
 * the day they joined; a level without a row in that period pays 0.
 */
@Getter
@Setter
@Entity
@Table(name = "event_level_fee")
public class EventLevelFee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private MembershipLevel level;

    // Start of the price period (Tallinn date); null = from the start
    @Column(name = "valid_from")
    private LocalDate validFrom;

    @NotNull
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;
}
