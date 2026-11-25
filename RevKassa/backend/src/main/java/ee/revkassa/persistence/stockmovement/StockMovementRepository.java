package ee.revkassa.persistence.stockmovement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select coalesce(sum(m.quantityDelta), 0) from StockMovement m where m.product.id = :productId")
    long sumQuantityDelta(Long productId);

}
