package ee.revkassa.persistence.eventlevelfee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface EventLevelFeeRepository extends JpaRepository<EventLevelFee, Long> {

    @Query("select f from EventLevelFee f where f.event.id = :eventId order by f.validFrom nulls first, f.level.id")
    List<EventLevelFee> findEventFees(Long eventId);

    @Query("select f from EventLevelFee f where f.event.id in :eventIds")
    List<EventLevelFee> findFeesOfEvents(Collection<Long> eventIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from EventLevelFee f where f.event.id = :eventId")
    void deleteEventFees(Long eventId);
}
