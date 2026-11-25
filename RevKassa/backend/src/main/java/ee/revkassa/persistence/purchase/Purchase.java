package ee.revkassa.persistence.purchase;

import ee.revkassa.persistence.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "purchase")
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // Sum of the lines at the prices of the moment of sale
    @NotNull
    @Column(name = "total_cents", nullable = false)
    private Long totalCents;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private PurchaseStatusEnum status;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_by")
    private Member reversedBy;

    // Made by the client per basket; the same id again returns this purchase instead of charging twice
    @Column(name = "request_id")
    private UUID requestId;

}
