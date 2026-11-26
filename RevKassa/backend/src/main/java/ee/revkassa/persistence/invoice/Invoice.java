package ee.revkassa.persistence.invoice;

import ee.revkassa.persistence.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A payment request emailed to a member in debt. It records the debt at the moment it was made and never changes
 * a balance. A FAILED invoice keeps its number and is sent again with it.
 */
@Getter
@Setter
@Entity
@Table(name = "invoice")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // "2026-0001", numbered per year; also the payment explanation
    @NotNull
    @Column(name = "number", nullable = false, length = Integer.MAX_VALUE)
    private String number;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // The address last used, copied from the member
    @NotNull
    @Column(name = "email_to", nullable = false, length = Integer.MAX_VALUE)
    private String emailTo;

    // The debt when the invoice was made (-balance_cents), > 0
    @NotNull
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private InvoiceStatusEnum status;

    // Mail server's reason when FAILED
    @Column(name = "error", length = Integer.MAX_VALUE)
    private String error;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sent_by", nullable = false)
    private Member sentBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // Last successful send
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

}
