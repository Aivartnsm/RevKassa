package ee.revkassa.persistence.organisation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrganisationRepository extends JpaRepository<Organisation, Integer> {

    // Row lock on the one row: invoices are numbered one after another, so two admins never take the same number
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organisation o where o.id = :id")
    Optional<Organisation> findForUpdate(Integer id);
}
