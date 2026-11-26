package ee.revkassa.persistence.payment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // id breaks ties within the same moment (a payment reported twice at once)
    @Query("""
            select p from Payment p
            where p.member.id = :memberId and p.status = :status
            order by p.reportedAt desc, p.id desc""")
    List<Payment> findMemberPayments(Long memberId, PaymentStatusEnum status, Pageable pageable);

    // The admin's queue: oldest first, as they come in on the bank statement
    @Query("""
            select p from Payment p join fetch p.member
            where p.status = ee.revkassa.persistence.payment.PaymentStatusEnum.PENDING
            order by p.reportedAt, p.id""")
    List<Payment> findPendingPayments(Pageable pageable);

    // Decided ones: the latest decisions first
    @Query("""
            select p from Payment p join fetch p.member left join fetch p.decidedBy
            where p.status = :status
            order by p.decidedAt desc, p.id desc""")
    List<Payment> findDecidedPayments(PaymentStatusEnum status, Pageable pageable);

    // Only the payer's id, so the payer's row can be locked before the payment itself is read
    @Query("select new ee.revkassa.persistence.payment.PaymentTotal(count(p), sum(p.amountCents)) "
            + "from Payment p where p.status = ee.revkassa.persistence.payment.PaymentStatusEnum.PENDING")
    PaymentTotal findPendingTotal();

    @Query("select p.member.id from Payment p where p.id = :paymentId")
    Optional<Long> findMemberId(Long paymentId);

}
