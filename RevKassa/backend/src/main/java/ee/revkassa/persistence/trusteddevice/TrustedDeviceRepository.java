package ee.revkassa.persistence.trusteddevice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    @Query("select d from TrustedDevice d where d.tokenHash = :tokenHash and d.revokedAt is null")
    Optional<TrustedDevice> findActiveByTokenHash(String tokenHash);

    // Active devices first, newest first; admins are fetched in the same query (no N+1)
    @Query("""
            select d from TrustedDevice d
            join fetch d.createdBy
            left join fetch d.revokedBy
            order by d.revokedAt desc nulls first, d.createdAt desc, d.id desc""")
    List<TrustedDevice> findAllForAdmin();

    // Row lock: two admins revoking at once cannot both succeed
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TrustedDevice d where d.id = :id")
    Optional<TrustedDevice> findForUpdate(Long id);

}
