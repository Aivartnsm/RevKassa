package ee.revkassa.persistence.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    String OPEN_STATUSES = "(ee.revkassa.persistence.event.EventStatusEnum.DRAFT, "
            + "ee.revkassa.persistence.event.EventStatusEnum.PUBLISHED)";
    String ACTIVE_PARTICIPATION = "select p.id from EventParticipant p where p.event = e and p.member.id = :memberId "
            + "and p.status = ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum.ACTIVE";

    // Open events (draft or published) first, the soonest first; then closed and cancelled ones, the newest first
    @Query("select e from Event e "
            + "order by case when e.status in " + OPEN_STATUSES + " then 0 else 1 end, "
            + "case when e.status in " + OPEN_STATUSES + " then e.startsAt end asc, "
            + "e.startsAt desc, e.id desc")
    List<Event> findAllEvents();

    @Query("select e from Event e where e.status in " + OPEN_STATUSES + " and e.startsAt >= :from order by e.startsAt, e.id")
    List<Event> findUpcoming(OffsetDateTime from, Pageable pageable);

    // Published events from a moment on, and earlier published ones the member takes part in; soonest first
    @Query("select e from Event e where e.status = ee.revkassa.persistence.event.EventStatusEnum.PUBLISHED "
            + "and (e.startsAt >= :from or exists (" + ACTIVE_PARTICIPATION + ")) order by e.startsAt, e.id")
    List<Event> findPublishedForMember(Long memberId, OffsetDateTime from);

    // Closed events the member took part in, the newest first
    @Query("select e from Event e where e.status = ee.revkassa.persistence.event.EventStatusEnum.CLOSED "
            + "and exists (" + ACTIVE_PARTICIPATION + ") order by e.startsAt desc, e.id desc")
    List<Event> findClosedForMember(Long memberId, Pageable pageable);

    @Query("select e from Event e join fetch e.createdBy where e.id = :id")
    Optional<Event> findWithCreator(Long id);

    // Row lock: status changes and participant changes of one event run one after another
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findForUpdate(Long id);
}
