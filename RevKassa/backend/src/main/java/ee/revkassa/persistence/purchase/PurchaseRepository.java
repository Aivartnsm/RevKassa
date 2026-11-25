package ee.revkassa.persistence.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Query("select p from Purchase p join fetch p.member where p.requestId = :requestId")
    Optional<Purchase> findByRequestId(UUID requestId);

    // Only the owner's id, so the owner's row can be locked before the purchase itself is read
    @Query("select p.member.id from Purchase p where p.id = :purchaseId")
    Optional<Long> findMemberId(Long purchaseId);

}
