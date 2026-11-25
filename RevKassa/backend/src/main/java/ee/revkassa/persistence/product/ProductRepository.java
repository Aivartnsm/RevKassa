package ee.revkassa.persistence.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select p from Product p where p.active = true order by p.sortOrder, p.name, p.id")
    List<Product> findActiveProducts();

    @Query("select p from Product p order by p.sortOrder, p.name, p.id")
    List<Product> findAllProducts();

    // One statement, so parallel sales and deliveries cannot overwrite each other; does not change the version
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty + :quantityDelta where p.id = :productId")
    int addToStock(Long productId, int quantityDelta);

    // Row locks in id order: a sale waits until the stocktake has compared and written; one order avoids deadlocks
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :productIds order by p.id")
    List<Product> findAllForUpdate(List<Long> productIds);

    @Query("select p.stockQty from Product p where p.id = :productId")
    int findStockQty(Long productId);

}
