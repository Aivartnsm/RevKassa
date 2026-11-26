package ee.revkassa.persistence.accountentry;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface AccountEntryRepository extends JpaRepository<AccountEntry, Long> {

    @Query("select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.member.id = :memberId")
    long sumAmountCents(Long memberId);

    // A Slice reads one row more than the page instead of counting all rows; id breaks ties within the same moment.
    // The payment and the fee charge come along in the same query: the payment method and the fee's month are part
    // of the description.
    @Query("""
            select e from AccountEntry e left join fetch e.payment left join fetch e.membershipCharge
            where e.member.id = :memberId
            order by e.createdAt desc, e.id desc""")
    Slice<AccountEntry> findMemberEntries(Long memberId, Pageable pageable);

    // Oldest first; after is exclusive and until inclusive, so back-to-back periods never share a row
    @Query("""
            select e from AccountEntry e left join fetch e.payment left join fetch e.membershipCharge
            where e.member.id = :memberId and e.createdAt > :after and e.createdAt <= :until
            order by e.createdAt, e.id""")
    List<AccountEntry> findMemberEntriesBetween(Long memberId, OffsetDateTime after, OffsetDateTime until);

}
