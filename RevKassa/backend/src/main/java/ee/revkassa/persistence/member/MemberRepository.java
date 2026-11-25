package ee.revkassa.persistence.member;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("select m from Member m where m.pinHmac = :pinHmac")
    Optional<Member> findByPinHmac(String pinHmac);

    @Query("select (count(m) > 0) from Member m where m.pinHmac = :pinHmac")
    boolean existsByPinHmac(String pinHmac);

    @Query("select m from Member m where lower(m.email) = lower(:email)")
    Optional<Member> findByEmail(String email);

    @Query("select (count(m) > 0) from Member m where m.id = :id and m.active = true")
    boolean isActiveMember(Long id);

    @Query("select (count(m) > 0) from Member m where m.role = ee.revkassa.persistence.member.MemberRoleEnum.ADMIN and m.active = true")
    boolean existsActiveAdmin();

    @Query("select m from Member m where m.role = ee.revkassa.persistence.member.MemberRoleEnum.ADMIN and m.active = true order by m.id")
    List<Member> findActiveAdmins();

    // Row lock: one member's purchases run one after another (same request twice cannot both pass the check).
    // Lock order everywhere: member first, then products in id order
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId")
    Optional<Member> findForUpdate(Long memberId);

    // One statement, so parallel purchases and payments cannot overwrite each other; does not change the version
    @Modifying(flushAutomatically = true)
    @Query("update Member m set m.balanceCents = m.balanceCents + :amountCents where m.id = :memberId")
    int addToBalance(Long memberId, long amountCents);

    @Query("select m.balanceCents from Member m where m.id = :memberId")
    long findBalanceCents(Long memberId);

}
