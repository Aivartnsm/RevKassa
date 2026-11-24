package ee.revkassa.persistence.trusteddevice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    @Query("select d from TrustedDevice d where d.tokenHash = :tokenHash and d.revokedAt is null")
    Optional<TrustedDevice> findActiveByTokenHash(String tokenHash);

}
