package ee.revkassa.persistence.eventparticipant;

import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A member on an event, with the level and fee copied when they joined (the price period of that day). The fee goes
 * to the balance only when an admin charges the event's fees (chargedAt). Rows are never deleted: a removed member's
 * row is REMOVED, and adding them again makes it ACTIVE with a new join time, level and fee.
 */
@Getter
@Setter
@Entity
@Table(name = "event_participant")
public class EventParticipant {
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
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private MembershipLevel level;

    @NotNull
    @Column(name = "fee_cents", nullable = false)
    private Long feeCents;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private EventParticipantStatusEnum status;

    // An admin, or the member themselves (signed up)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by", nullable = false)
    private Member addedBy;

    @NotNull
    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    // When the fee went to the balance; null = not charged yet
    @Column(name = "charged_at")
    private OffsetDateTime chargedAt;

    public boolean isActive() {
        return status == EventParticipantStatusEnum.ACTIVE;
    }

    public boolean isCharged() {
        return chargedAt != null;
    }

    // Added by the member themselves, not by an admin
    public boolean isSelfSignup() {
        return addedBy.getId().equals(member.getId());
    }
}
