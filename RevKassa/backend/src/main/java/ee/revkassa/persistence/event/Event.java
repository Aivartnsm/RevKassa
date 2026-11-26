package ee.revkassa.persistence.event;

import ee.revkassa.persistence.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

/**
 * A club event with fees per membership level and price period (EventLevelFee). A DRAFT is hidden from members;
 * only DRAFT and PUBLISHED events can be changed.
 */
@Getter
@Setter
@Entity
@Table(name = "event")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @NotNull
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "location", length = Integer.MAX_VALUE)
    private String location;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private EventStatusEnum status;

    // Members can sign up themselves; only on a PUBLISHED event (database CHECK)
    @NotNull
    @ColumnDefault("false")
    @Column(name = "signup_open", nullable = false)
    private Boolean signupOpen;

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

    @NotNull
    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isEditable() {
        return status == EventStatusEnum.DRAFT || status == EventStatusEnum.PUBLISHED;
    }
}
