package ee.revkassa.persistence.eventparticipant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    // Events without active participants are missing from the result
    @Query("""
            select new ee.revkassa.persistence.eventparticipant.ParticipantTotal(p.event.id, count(p), sum(p.feeCents))
            from EventParticipant p
            where p.event.id in :eventIds
              and p.status = ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum.ACTIVE
            group by p.event.id""")
    List<ParticipantTotal> findActiveTotals(Collection<Long> eventIds);
}
