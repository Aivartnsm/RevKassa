package ee.revkassa.persistence.eventparticipant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    String ACTIVE = "ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum.ACTIVE";

    // Events without active participants are missing from the result
    @Query("select new ee.revkassa.persistence.eventparticipant.ParticipantTotal(p.event.id, count(p), sum(p.feeCents)) "
            + "from EventParticipant p where p.event.id in :eventIds and p.status = " + ACTIVE + " group by p.event.id")
    List<ParticipantTotal> findActiveTotals(Collection<Long> eventIds);

    // Active ones by name, then removed ones by name
    @Query("select p from EventParticipant p join fetch p.member m join fetch p.level join fetch p.addedBy "
            + "where p.event.id = :eventId "
            + "order by case when p.status = " + ACTIVE + " then 0 else 1 end, lower(m.displayName), m.id")
    List<EventParticipant> findEventParticipants(Long eventId);

    @Query("select p from EventParticipant p where p.event.id = :eventId and p.member.id in :memberIds")
    List<EventParticipant> findByMembers(Long eventId, Collection<Long> memberIds);

    @Query("select p from EventParticipant p join fetch p.member where p.event.id = :eventId and p.member.id = :memberId")
    Optional<EventParticipant> findByMember(Long eventId, Long memberId);

    @Query("select p from EventParticipant p join fetch p.member "
            + "where p.event.id = :eventId and p.status = " + ACTIVE + " order by p.id")
    List<EventParticipant> findActive(Long eventId);

    @Query("select p from EventParticipant p join fetch p.member "
            + "where p.event.id = :eventId and p.status = " + ACTIVE + " and p.chargedAt is null order by p.id")
    List<EventParticipant> findUncharged(Long eventId);
}
