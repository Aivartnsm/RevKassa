package ee.revkassa.persistence.eventbudgetline;

import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

/**
 * One planned cost of an event. Planning data only: it never touches balances or stock, and it can be deleted.
 */
@Getter
@Setter
@Entity
@Table(name = "event_budget_line")
public class EventBudgetLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = Integer.MAX_VALUE)
    private BudgetCategoryEnum category;

    @NotNull
    @Column(name = "description", nullable = false, length = Integer.MAX_VALUE)
    private String description;

    @NotNull
    @Column(name = "planned_cents", nullable = false)
    private Long plannedCents;

    // null = not bought yet / receipt missing
    @Column(name = "actual_cents")
    private Long actualCents;

    @Column(name = "note", length = Integer.MAX_VALUE)
    private String note;

    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MANUAL'")
    @Column(name = "source", nullable = false, length = Integer.MAX_VALUE)
    private BudgetLineSourceEnum source;

    // Order within the category
    @NotNull
    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Member createdBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
