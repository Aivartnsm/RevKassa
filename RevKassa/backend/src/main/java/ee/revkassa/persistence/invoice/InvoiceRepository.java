package ee.revkassa.persistence.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // The running number after "YYYY-" as a number, so "2026-10000" still counts as more than "2026-9999"
    @Query("select max(cast(substring(i.number, 6) as Integer)) from Invoice i where i.number like :yearPrefix")
    Optional<Integer> findLastNumberOfYear(String yearPrefix);

    // The start of the next invoice's balance lines: the last invoice that reached the member before the given moment
    @Query("""
            select max(i.createdAt) from Invoice i
            where i.member.id = :memberId and i.status = ee.revkassa.persistence.invoice.InvoiceStatusEnum.SENT
              and i.createdAt < :before""")
    Optional<OffsetDateTime> findLastSentCreatedAt(Long memberId, OffsetDateTime before);

    // Each listed member's newest invoice, whatever its status
    @Query("""
            select i from Invoice i
            where i.member.id in :memberIds
              and i.id = (select max(i2.id) from Invoice i2 where i2.member = i.member)""")
    List<Invoice> findLatestOfMembers(Collection<Long> memberIds);

    @Query("""
            select i.member.id from Invoice i
            where i.member.id in :memberIds and i.status = ee.revkassa.persistence.invoice.InvoiceStatusEnum.SENT
              and i.sentAt >= :since""")
    List<Long> findMemberIdsSentSince(Collection<Long> memberIds, OffsetDateTime since);

    // A Slice reads one row more than the page instead of counting all rows
    @Query("""
            select i from Invoice i join fetch i.member join fetch i.sentBy
            where :memberId is null or i.member.id = :memberId
            order by i.createdAt desc, i.id desc""")
    Slice<Invoice> findInvoices(Long memberId, Pageable pageable);

    // Row lock: the same failed invoice resent twice at once is sent only once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i join fetch i.member where i.id = :id")
    Optional<Invoice> findForUpdate(Long id);
}
