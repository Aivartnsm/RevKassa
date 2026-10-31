package ee.revkassa.persistence.member;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "member")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "email", length = Integer.MAX_VALUE)
    private String email;

    @NotNull
    @Column(name = "display_name", nullable = false, length = Integer.MAX_VALUE)
    private String displayName;

    @NotNull
    @Column(name = "pin_hmac", nullable = false, length = Integer.MAX_VALUE)
    private String pinHmac;

    @Column(name = "password_hash", length = Integer.MAX_VALUE)
    private String passwordHash;

    @NotNull
    @Column(name = "role", nullable = false, length = Integer.MAX_VALUE)
    private String role;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "active", nullable = false)
    private Boolean active;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "fee_exempt", nullable = false)
    private Boolean feeExempt;

    @Column(name = "pin_changed_at")
    private OffsetDateTime pinChangedAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private MembershipLevel level;


}