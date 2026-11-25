package ee.revkassa.persistence.accountentry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountEntryRepository extends JpaRepository<AccountEntry, Long> {

    @Query("select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.member.id = :memberId")
    long sumAmountCents(Long memberId);

}
