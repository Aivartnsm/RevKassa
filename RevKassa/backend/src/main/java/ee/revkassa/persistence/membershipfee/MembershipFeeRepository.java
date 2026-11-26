package ee.revkassa.persistence.membershipfee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MembershipFeeRepository extends JpaRepository<MembershipFee, Long> {

    // A handful of rows per level (one per fee change), so all of them fit in one screen
    @Query("select f from MembershipFee f join fetch f.createdBy order by f.level.id, f.validFrom desc")
    List<MembershipFee> findAllFees();

    @Query("select f from MembershipFee f where f.level.id = :levelId and f.validFrom = :validFrom")
    Optional<MembershipFee> findByLevelAndMonth(Long levelId, LocalDate validFrom);

    // The level's latest row that has started by this month
    @Query("select f.amountCents from MembershipFee f where f.level.id = :levelId and f.validFrom <= :month "
            + "order by f.validFrom desc limit 1")
    Optional<Long> findAmountFor(Long levelId, LocalDate month);

    // membership_charge gets its entity with the monthly fee job (B23)
    @Query(value = "select exists (select 1 from membership_charge c where c.level_id = :levelId and c.month = :month)",
            nativeQuery = true)
    boolean isMonthCharged(Long levelId, LocalDate month);

}
