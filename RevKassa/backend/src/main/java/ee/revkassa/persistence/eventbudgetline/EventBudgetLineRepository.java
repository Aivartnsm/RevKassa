package ee.revkassa.persistence.eventbudgetline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventBudgetLineRepository extends JpaRepository<EventBudgetLine, Long> {

    // Ordered within a category; the categories themselves are ordered by BudgetCategoryEnum in the service
    @Query("select l from EventBudgetLine l where l.event.id = :eventId order by l.sortOrder, l.id")
    List<EventBudgetLine> findEventLines(Long eventId);

    @Query("select l from EventBudgetLine l where l.event.id = :eventId and l.id = :lineId")
    Optional<EventBudgetLine> findEventLine(Long eventId, Long lineId);

    @Query("select coalesce(max(l.sortOrder), -1) from EventBudgetLine l "
            + "where l.event.id = :eventId and l.category = :category")
    int findLastSortOrder(Long eventId, BudgetCategoryEnum category);

    // Events without budget lines are missing from the result
    @Query("select new ee.revkassa.persistence.eventbudgetline.BudgetTotal(l.event.id, sum(l.plannedCents), "
            + "coalesce(sum(l.actualCents), 0)) from EventBudgetLine l where l.event.id in :eventIds group by l.event.id")
    List<BudgetTotal> findTotals(Collection<Long> eventIds);
}
