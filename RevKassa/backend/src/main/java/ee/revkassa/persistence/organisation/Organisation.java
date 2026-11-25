package ee.revkassa.persistence.organisation;

import ee.revkassa.persistence.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * The one row (id = 1) with the payee name and IBAN that members transfer to and invoices show. An admin sets them;
 * both are null until then.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation")
public class Organisation {

    public static final Integer ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    // Without spaces, in capitals
    @Column(name = "iban", length = Integer.MAX_VALUE)
    private String iban;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Member updatedBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @NotNull
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
