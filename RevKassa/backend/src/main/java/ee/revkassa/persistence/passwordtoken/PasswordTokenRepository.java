package ee.revkassa.persistence.passwordtoken;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PasswordTokenRepository extends JpaRepository<PasswordToken, Long> {

    // Row lock: the same link opened twice at once can set the password only once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordToken t join fetch t.member where t.tokenHash = :tokenHash")
    Optional<PasswordToken> findForUpdateByTokenHash(String tokenHash);

    // Not expired yet or not: every link without used_at is closed when a new one is sent or one is used
    @Query("select t from PasswordToken t where t.member.id = :memberId and t.usedAt is null")
    List<PasswordToken> findAllOpenByMemberId(Long memberId);

}
