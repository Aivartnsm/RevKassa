package ee.revkassa.persistence.member;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
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

    @Query("select (count(m) > 0) from Member m where m.id = :id and m.role = ee.revkassa.persistence.member.MemberRoleEnum.ADMIN and m.active = true")
    boolean isActiveAdmin(Long id);

    // Row locks in id order: two admins removing each other's admin rights at once run one after the other,
    // so the second one sees the first and the last active admin always stays
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.role = ee.revkassa.persistence.member.MemberRoleEnum.ADMIN and m.active = true order by m.id")
    List<Member> findActiveAdminsForUpdate();

    @Query("select m from Member m join fetch m.level order by lower(m.displayName), m.id")
    List<Member> findAllMembers();

    @Query("select m from Member m join fetch m.level where m.id in :ids")
    List<Member> findWithLevels(Collection<Long> ids);

    // Row lock: one member's purchases run one after another (same request twice cannot both pass the check).
    // Lock order everywhere: member first, then products in id order
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId")
    Optional<Member> findForUpdate(Long memberId);

    // One statement, so parallel purchases and payments cannot overwrite each other; does not change the version
    @Modifying(flushAutomatically = true)
    @Query("update Member m set m.balanceCents = m.balanceCents + :amountCents where m.id = :memberId")
    void addToBalance(Long memberId, long amountCents);

    // Also inactive members: a former member's debt is still owed
    @Query("select new ee.revkassa.persistence.member.DebtTotal(count(m), -sum(m.balanceCents)) "
            + "from Member m where m.balanceCents < 0")
    DebtTotal findDebtTotal();

    @Query("select m from Member m where m.balanceCents < 0 order by m.balanceCents, m.id")
    List<Member> findLargestDebtors(Pageable pageable);

    @Query("select m.balanceCents from Member m where m.id = :memberId")
    long findBalanceCents(Long memberId);

}
