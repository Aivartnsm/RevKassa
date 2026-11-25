package ee.revkassa.persistence.purchaseline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PurchaseLineRepository extends JpaRepository<PurchaseLine, Long> {

    @Query("select l from PurchaseLine l join fetch l.product where l.purchase.id = :purchaseId order by l.id")
    List<PurchaseLine> findAllByPurchaseId(Long purchaseId);

    @Query("select l from PurchaseLine l join fetch l.product where l.purchase.id in :purchaseIds order by l.id")
    List<PurchaseLine> findAllByPurchaseIds(List<Long> purchaseIds);

}
