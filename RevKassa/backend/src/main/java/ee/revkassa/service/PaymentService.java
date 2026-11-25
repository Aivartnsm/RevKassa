package ee.revkassa.service;

import ee.revkassa.controller.admin.payment.dto.AdminPaymentCreateRequestDto;
import ee.revkassa.controller.admin.payment.dto.AdminPaymentDto;
import ee.revkassa.controller.admin.payment.dto.PaymentRejectRequestDto;
import ee.revkassa.controller.payment.dto.PaymentCreateRequestDto;
import ee.revkassa.controller.payment.dto.PaymentDetailsDto;
import ee.revkassa.controller.payment.dto.PaymentDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.DataNotFoundException;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.organisation.Organisation;
import ee.revkassa.persistence.organisation.OrganisationRepository;
import ee.revkassa.persistence.payment.Payment;
import ee.revkassa.persistence.payment.PaymentMapper;
import ee.revkassa.persistence.payment.PaymentMethodEnum;
import ee.revkassa.persistence.payment.PaymentRepository;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Members pay by ordinary bank transfer to the organisation's IBAN and then report the payment here. A reported payment
 * is only PENDING: the balance and the ledger stay untouched until an admin sees the money in the bank and confirms it.
 * Confirming writes exactly one PAYMENT ledger row; an admin may also enter a payment as confirmed at once.
 * Deciding locks the payer's member row first (the same lock order as purchases), so two decisions on one payment
 * run one after the other and the second sees the first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final int MAX_LISTED_PAYMENTS = 50;
    public static final int MAX_ADMIN_LISTED_PAYMENTS = 100;

    // Plain characters only: some banks drop or refuse others in a transfer description
    private static final String DESCRIPTION_PREFIX = "RevKassa - ";

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OrganisationRepository organisationRepository;
    private final MemberRepository memberRepository;
    private final AccountService accountService;
    private final Clock clock;

    // ---- member ----

    @Transactional(readOnly = true)
    public PaymentDetailsDto getPaymentDetails(Long memberId) {
        Organisation organisation = getConfiguredOrganisation();
        Member member = getValidMemberBy(memberId);
        return new PaymentDetailsDto(organisation.getName(), organisation.getIban(),
                DESCRIPTION_PREFIX + member.getDisplayName());
    }

    @Transactional
    public PaymentDto reportPayment(Long memberId, PaymentCreateRequestDto request) {
        Member member = getValidMemberBy(memberId);
        Payment payment = paymentRepository.save(
                createPayment(member, request.getAmountCents(), request.getReference(), PaymentStatusEnum.PENDING));
        log.info("Payment reported: paymentId={}, memberId={}", payment.getId(), memberId);
        return paymentMapper.toPaymentDto(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getMemberPayments(Long memberId, PaymentStatusEnum status) {
        List<Payment> payments = paymentRepository.findMemberPayments(memberId, status,
                PageRequest.of(0, MAX_LISTED_PAYMENTS));
        return paymentMapper.toPaymentDtos(payments);
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public List<AdminPaymentDto> getPayments(PaymentStatusEnum status) {
        PageRequest firstPage = PageRequest.of(0, MAX_ADMIN_LISTED_PAYMENTS);
        List<Payment> payments = status == PaymentStatusEnum.PENDING
                ? paymentRepository.findPendingPayments(firstPage)
                : paymentRepository.findDecidedPayments(status, firstPage);
        return paymentMapper.toAdminPaymentDtos(payments);
    }

    @Transactional
    public AdminPaymentDto confirmPayment(Long paymentId, Long adminId) {
        Member payer = getValidMemberForUpdateBy(getValidPayerIdBy(paymentId));
        Member admin = getValidMemberBy(adminId);
        Payment payment = getValidPendingPaymentBy(paymentId);
        markDecided(payment, PaymentStatusEnum.CONFIRMED, admin, null);
        accountService.post(AccountPosting.payment(payment, admin));
        log.info("Payment confirmed: paymentId={}, memberId={}, adminId={}", paymentId, payer.getId(), adminId);
        return paymentMapper.toAdminPaymentDto(payment);
    }

    @Transactional
    public AdminPaymentDto rejectPayment(Long paymentId, PaymentRejectRequestDto request, Long adminId) {
        Member payer = getValidMemberForUpdateBy(getValidPayerIdBy(paymentId));
        Member admin = getValidMemberBy(adminId);
        Payment payment = getValidPendingPaymentBy(paymentId);
        markDecided(payment, PaymentStatusEnum.REJECTED, admin, request == null ? null : clean(request.getNote()));
        log.info("Payment rejected: paymentId={}, memberId={}, adminId={}", paymentId, payer.getId(), adminId);
        return paymentMapper.toAdminPaymentDto(payment);
    }

    // Also for a deactivated member: a former member may still settle their debt
    @Transactional
    public AdminPaymentDto createConfirmedPayment(AdminPaymentCreateRequestDto request, Long adminId) {
        Member payer = getValidMemberForUpdateBy(request.getMemberId());
        Member admin = getValidMemberBy(adminId);
        Payment payment = createPayment(payer, request.getAmountCents(), request.getReference(),
                PaymentStatusEnum.CONFIRMED);
        markDecided(payment, PaymentStatusEnum.CONFIRMED, admin, null);
        paymentRepository.save(payment);
        accountService.post(AccountPosting.payment(payment, admin));
        log.info("Payment entered as confirmed: paymentId={}, memberId={}, adminId={}", payment.getId(), payer.getId(),
                adminId);
        return paymentMapper.toAdminPaymentDto(payment);
    }

    // ---- helpers ----

    // Until an admin has entered both, there is nowhere to pay to
    private Organisation getConfiguredOrganisation() {
        return organisationRepository.findById(Organisation.ID)
                .filter(organisation -> organisation.getName() != null && organisation.getIban() != null)
                .orElseThrow(() -> new DataNotFoundException(Error.PAYMENT_DETAILS_NOT_SET));
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private Payment createPayment(Member member, long amountCents, String reference, PaymentStatusEnum status) {
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmountCents(amountCents);
        payment.setMethod(PaymentMethodEnum.BANK_TRANSFER);
        payment.setReference(clean(reference));
        payment.setStatus(status);
        payment.setReportedAt(OffsetDateTime.now(clock));
        return payment;
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private Long getValidPayerIdBy(Long paymentId) {
        return paymentRepository.findMemberId(paymentId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private Member getValidMemberForUpdateBy(Long memberId) {
        return memberRepository.findForUpdate(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Read after the payer's row is locked: a parallel decision on the same payment has finished by now
    private Payment getValidPendingPaymentBy(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
        if (payment.getStatus() != PaymentStatusEnum.PENDING) {
            throw new ForbiddenException(Error.PAYMENT_ALREADY_DECIDED);
        }
        return payment;
    }

    private void markDecided(Payment payment, PaymentStatusEnum status, Member admin, String note) {
        payment.setStatus(status);
        payment.setDecidedBy(admin);
        payment.setDecidedAt(OffsetDateTime.now(clock));
        payment.setDecisionNote(note);
    }
}
