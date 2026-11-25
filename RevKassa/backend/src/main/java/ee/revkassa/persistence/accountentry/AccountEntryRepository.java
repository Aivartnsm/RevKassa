package ee.revkassa.persistence.accountentry;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountEntryRepository extends JpaRepository<AccountEntry, Long> {

    @Query("select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.member.id = :memberId")
    long sumAmountCents(Long memberId);

    // A Slice reads one row more than the page instead of counting all rows; id breaks ties within the same moment.
    // The payment comes along in the same query: its method is part of the description.
    @Query("""
            select e from AccountEntry e left join fetch e.payment
            where e.member.id = :memberId
            order by e.createdAt desc, e.id desc""")
    Slice<AccountEntry> findMemberEntries(Long memberId, Pageable pageable);

}
