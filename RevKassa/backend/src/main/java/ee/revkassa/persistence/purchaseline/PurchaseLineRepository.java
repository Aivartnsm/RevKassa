package ee.revkassa.persistence.purchaseline;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseLineRepository extends JpaRepository<PurchaseLine, Long> {

    @Query("select l from PurchaseLine l join fetch l.product where l.purchase.id = :purchaseId order by l.id")
    List<PurchaseLine> findAllByPurchaseId(Long purchaseId);

    // Undone purchases do not count. from inclusive, to exclusive
    @Query("""
            select new ee.revkassa.persistence.purchaseline.ProductSales(l.product.id, l.product.name, sum(l.quantity))
            from PurchaseLine l
            where l.purchase.status = ee.revkassa.persistence.purchase.PurchaseStatusEnum.COMPLETED
              and l.purchase.createdAt >= :from and l.purchase.createdAt < :to
            group by l.product.id, l.product.name
            order by sum(l.quantity) desc, l.product.name""")
    List<ProductSales> findTopSellers(OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    /**
     * Completed sales per product and month (club time: the zone of ClockConfig.APP_ZONE), from inclusive, to exclusive. Cost per unit: the purchase
     * price stored with the sale, else the product's current one; units with neither are counted in unitsWithoutCost
     * and left out of costCents.
     */
    @Query(value = """
            select l.product_id as "productId",
                   to_char(p.created_at at time zone 'Europe/Tallinn', 'YYYY-MM') as "month",
                   sum(l.quantity) as "quantity",
                   sum(l.quantity * l.unit_price_cents) as "revenueCents",
                   coalesce(sum(l.quantity * coalesce(l.unit_cost_cents, pr.cost_cents)), 0) as "costCents",
                   sum(case when coalesce(l.unit_cost_cents, pr.cost_cents) is null then l.quantity else 0 end)
                       as "unitsWithoutCost"
            from purchase_line l
            join purchase p on p.id = l.purchase_id
            join product pr on pr.id = l.product_id
            where p.status = 'COMPLETED' and p.created_at >= :from and p.created_at < :to
            group by l.product_id, 2""", nativeQuery = true)
    List<ProductMonthSales> findMonthlySales(OffsetDateTime from, OffsetDateTime to);

    @Query("select min(p.createdAt) from Purchase p where p.status = ee.revkassa.persistence.purchase.PurchaseStatusEnum.COMPLETED")
    Optional<OffsetDateTime> findFirstSaleTime();

    @Query("select l from PurchaseLine l join fetch l.product where l.purchase.id in :purchaseIds order by l.id")
    List<PurchaseLine> findAllByPurchaseIds(List<Long> purchaseIds);

}
