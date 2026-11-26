package ee.revkassa.persistence.membershipcharge;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One member's monthly fee for one month, with the level and amount of the moment it was charged: a later fee or level
 * change never alters it. At most one per member and month (unique index).
 */
@Getter
@Setter
@Entity
@Table(name = "membership_charge")
public class MembershipCharge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // Always the first day of a month
    @NotNull
    @Column(name = "month", nullable = false)
    private LocalDate month;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private MembershipLevel level;

    @NotNull
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private MembershipChargeStatusEnum status;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waived_by")
    private Member waivedBy;

    @Column(name = "waived_at")
    private OffsetDateTime waivedAt;

    @Column(name = "waive_note", length = Integer.MAX_VALUE)
    private String waiveNote;

}
