package ee.revkassa.persistence.memberfavourite;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.product.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

/**
 * One product a member has starred. A preference, not a ledger: the member's whole list is replaced on save.
 */
@Getter
@Setter
@Entity
@Table(name = "member_favourite")
public class MemberFavourite {
    @EmbeddedId
    private MemberFavouriteId id = new MemberFavouriteId();

    @MapsId("memberId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Order in the member's buying view, 0..n-1 within one member
    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
