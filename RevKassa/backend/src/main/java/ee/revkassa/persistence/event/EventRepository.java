package ee.revkassa.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // Planned events first, the soonest first; then closed and cancelled ones, the newest first
    @Query("""
            select e from Event e
            order by case when e.status = ee.revkassa.persistence.event.EventStatusEnum.PLANNED then 0 else 1 end,
                     case when e.status = ee.revkassa.persistence.event.EventStatusEnum.PLANNED then e.startsAt end asc,
                     e.startsAt desc, e.id desc""")
    List<Event> findAllEvents();

    @Query("""
            select e from Event e
            where e.status = ee.revkassa.persistence.event.EventStatusEnum.PLANNED and e.startsAt >= :from
            order by e.startsAt, e.id""")
    List<Event> findUpcoming(OffsetDateTime from, Pageable pageable);

    @Query("select e from Event e join fetch e.createdBy where e.id = :id")
    Optional<Event> findWithCreator(Long id);
}
