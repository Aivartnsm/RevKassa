package ee.revkassa.persistence.membershipcharge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MembershipChargeRepository extends JpaRepository<MembershipCharge, Long> {

    // Members to try this month: active, not exempt, joined before the month and not charged for it yet.
    // Each one is checked again under its row lock before charging
    @Query("""
            select m.id from Member m
            where m.active = true and m.feeExempt = false and m.createdAt < :monthStart
              and not exists (select 1 from MembershipCharge c where c.member.id = m.id and c.month = :month)
            order by m.id""")
    List<Long> findChargeableMemberIds(LocalDate month, OffsetDateTime monthStart);

    @Query("""
            select c from MembershipCharge c join fetch c.member join fetch c.level left join fetch c.waivedBy
            where c.month = :month
            order by lower(c.member.displayName), c.id""")
    List<MembershipCharge> findMonthCharges(LocalDate month);

    // Only the member's id, so the member's row can be locked before the charge itself is read
    @Query("select c.member.id from MembershipCharge c where c.id = :chargeId")
    Optional<Long> findMemberId(Long chargeId);

    @Query("select (count(c) > 0) from MembershipCharge c where c.member.id = :memberId and c.month = :month")
    boolean isCharged(Long memberId, LocalDate month);

}
