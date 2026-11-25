package ee.revkassa.persistence.payment;

import ee.revkassa.persistence.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

/**
 * A payment the member reported (PENDING) or an admin entered. Only a CONFIRMED payment changes the balance, through one
 * PAYMENT row in the ledger (B16).
 */
@Getter
@Setter
@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @NotNull
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = Integer.MAX_VALUE)
    private PaymentMethodEnum method;

    // The member's note or the bank reference, as typed
    @Column(name = "reference", length = Integer.MAX_VALUE)
    private String reference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private PaymentStatusEnum status;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Member decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    // e.g. why it was rejected
    @Column(name = "decision_note", length = Integer.MAX_VALUE)
    private String decisionNote;

}
