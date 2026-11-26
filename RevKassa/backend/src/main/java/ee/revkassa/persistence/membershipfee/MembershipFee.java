package ee.revkassa.persistence.membershipfee;

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
 * A level's monthly fee from validFrom on, until the level's next row. A row whose month has been charged never
 * changes again, so old months keep their old amounts.
 */
@Getter
@Setter
@Entity
@Table(name = "membership_fee")
public class MembershipFee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private MembershipLevel level;

    // 0 = members of this level are not charged
    @NotNull
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    // Always the first day of a month
    @NotNull
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Member createdBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
