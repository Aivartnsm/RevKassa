package ee.revkassa.persistence.memberfavourite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MemberFavouriteRepository extends JpaRepository<MemberFavourite, MemberFavouriteId> {

    @Query("select f.id.productId from MemberFavourite f where f.id.memberId = :memberId order by f.position")
    List<Long> findProductIds(Long memberId);

    // One statement that runs at once, so the new list can reuse the same positions and products
    @Modifying(flushAutomatically = true)
    @Query("delete from MemberFavourite f where f.id.memberId = :memberId")
    int deleteByMemberId(Long memberId);

}
