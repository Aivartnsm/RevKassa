package ee.revkassa.persistence.accountentry;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.payment.Payment;
import ee.revkassa.persistence.purchase.Purchase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

/**
 * One row of the money ledger. Rows are only added, never changed or deleted: a correction is a new row.
 * The links to membership charge and event participant are plain ids until those entities exist (B20, B22); each
 * becomes a relation then.
 */
@Getter
@Setter
@Entity
@Table(name = "account_entry")
public class AccountEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = Integer.MAX_VALUE)
    private AccountEntryTypeEnum type;

    @NotNull
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "membership_charge_id")
    private Long membershipChargeId;

    @Column(name = "event_participant_id")
    private Long eventParticipantId;

    @Column(name = "note", length = Integer.MAX_VALUE)
    private String note;

    // null = written by the app itself (monthly fee job)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
