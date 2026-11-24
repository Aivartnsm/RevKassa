package ee.revkassa.persistence.membershiplevel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MembershipLevelRepository extends JpaRepository<MembershipLevel, Long> {

    @Query("select l from MembershipLevel l where l.active = true order by l.sortOrder, l.id")
    List<MembershipLevel> findAllActive();

}
