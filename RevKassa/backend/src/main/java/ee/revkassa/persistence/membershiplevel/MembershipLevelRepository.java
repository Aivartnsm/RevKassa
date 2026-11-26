package ee.revkassa.persistence.membershiplevel;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MembershipLevelRepository extends JpaRepository<MembershipLevel, Long> {

    @Query("select l from MembershipLevel l where l.active = true order by l.sortOrder, l.id")
    List<MembershipLevel> findAllActive();

    @Query("select l from MembershipLevel l order by l.sortOrder, l.id")
    List<MembershipLevel> findAllLevels();

    // Row locks: two admins deactivating the last two active levels at once run one after the other,
    // so the second one sees the first and one active level always stays
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MembershipLevel l where l.active = true order by l.id")
    List<MembershipLevel> findAllActiveForUpdate();

    // Row locks in id order: two fee saves for the same level run one after the other, and the monthly fee job reads
    // a level's fee only while no fee save holds its row
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MembershipLevel l where l.id in :levelIds order by l.id")
    List<MembershipLevel> findAllForUpdate(List<Long> levelIds);

    // Shared row lock while the monthly fee job reads the level's fee: a fee save for the level (which holds the row
    // for update) runs wholly before or after the charge, never in between
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select l from MembershipLevel l where l.id = :levelId")
    Optional<MembershipLevel> findForShare(Long levelId);

    @Query("select l from MembershipLevel l where lower(l.name) = lower(:name)")
    Optional<MembershipLevel> findByName(String name);

    @Query("select coalesce(max(l.sortOrder), 0) from MembershipLevel l")
    int findMaxSortOrder();

    @Query("select new ee.revkassa.persistence.membershiplevel.LevelMemberCount(m.level.id, count(m)) "
            + "from Member m where m.active = true group by m.level.id")
    List<LevelMemberCount> countActiveMembersByLevel();

    @Query("select count(m) from Member m where m.active = true and m.level.id = :levelId")
    long countActiveMembers(Long levelId);

    // Everything that refers to a level; such a level can only be deactivated. Some of these tables get their
    // entities in later tasks, hence plain SQL
    String USED_LEVEL_CONDITION = """
            exists (select 1 from member m where m.level_id = l.id)
            or exists (select 1 from membership_fee f where f.level_id = l.id)
            or exists (select 1 from membership_charge c where c.level_id = l.id)
            or exists (select 1 from event_level_fee e where e.level_id = l.id)
            or exists (select 1 from event_participant p where p.level_id = l.id)""";

    @Query(value = "select l.id from membership_level l where " + USED_LEVEL_CONDITION, nativeQuery = true)
    List<Long> findUsedLevelIds();

    @Query(value = "select exists (select 1 from membership_level l where l.id = :levelId and (" + USED_LEVEL_CONDITION + "))",
            nativeQuery = true)
    boolean isInUse(Long levelId);

}
