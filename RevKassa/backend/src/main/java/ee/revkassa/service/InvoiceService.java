package ee.revkassa.service;

import ee.revkassa.controller.admin.invoice.dto.InvoiceCandidateDto;
import ee.revkassa.controller.admin.invoice.dto.InvoiceCreateRequestDto;
import ee.revkassa.controller.admin.invoice.dto.InvoiceDto;
import ee.revkassa.controller.admin.invoice.dto.InvoicePageDto;
import ee.revkassa.controller.admin.invoice.dto.InvoicePreviewDto;
import ee.revkassa.controller.admin.invoice.dto.InvoiceSendResultDto;
import ee.revkassa.controller.admin.invoice.dto.InvoiceSendStatusEnum;
import ee.revkassa.controller.admin.invoice.dto.InvoiceSkipReasonEnum;
import ee.revkassa.controller.me.dto.AccountEntryDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.config.RevkassaProperties;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.DataNotFoundException;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.mail.MailSendFailedException;
import ee.revkassa.infrastructure.mail.Mailer;
import ee.revkassa.persistence.invoice.Invoice;
import ee.revkassa.persistence.invoice.InvoiceMapper;
import ee.revkassa.persistence.invoice.InvoiceRepository;
import ee.revkassa.persistence.invoice.InvoiceStatusEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.organisation.Organisation;
import ee.revkassa.persistence.organisation.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Invoice emails (B26): a payment request for a member's debt. An invoice never changes a balance; the member reports
 * the payment in the app as usual. Each member is invoiced in a transaction of its own, so one bad address does not
 * stop the others. Numbers run per year ("2026-0001") under a lock on the organisation row, and a FAILED invoice keeps
 * its number when it is resent. The balance lines start at the last invoice that reached the member, at most 60 days
 * back. Logs carry ids and numbers only, never addresses or amounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    public static final int PAGE_SIZE = 20;

    static final Duration MAX_LINES_PERIOD = Duration.ofDays(60);
    static final Duration RECENTLY_INVOICED = Duration.ofDays(7);
    private static final String NUMBER_FORMAT = "%d-%04d";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final MemberRepository memberRepository;
    private final OrganisationRepository organisationRepository;
    private final AccountEntryService accountEntryService;
    private final Mailer mailer;
    private final RevkassaProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Every member in debt, also deactivated ones, largest debt first.
     */
    @Transactional(readOnly = true)
    public List<InvoiceCandidateDto> getCandidates() {
        List<Member> debtors = memberRepository.findLargestDebtors(Pageable.unpaged());
        if (debtors.isEmpty()) {
            return List.of();
        }
        List<Long> memberIds = debtors.stream().map(Member::getId).toList();
        Map<Long, Invoice> latestInvoices = invoiceRepository.findLatestOfMembers(memberIds).stream()
                .collect(Collectors.toMap(invoice -> invoice.getMember().getId(), Function.identity()));
        Set<Long> recentlyInvoiced = new HashSet<>(
                invoiceRepository.findMemberIdsSentSince(memberIds, now().minus(RECENTLY_INVOICED)));
        return debtors.stream()
                .map(member -> toCandidateDto(member, latestInvoices.get(member.getId()), recentlyInvoiced))
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoicePreviewDto getPreview(Long memberId) {
        Organisation organisation = getConfiguredOrganisation();
        Member member = getValidMemberBy(memberId);
        long balanceCents = memberRepository.findBalanceCents(memberId);
        if (balanceCents >= 0) {
            throw new ForbiddenException(Error.MEMBER_NOT_IN_DEBT);
        }
        // Not saved: the same email the member would get now, with the next free number
        Invoice invoice = createInvoice(member, balanceCents, null);
        InvoiceEmail email = composeEmail(invoice, organisation);
        return new InvoicePreviewDto(invoice.getNumber(), invoice.getAmountCents(), invoice.getDueDate(),
                email.to(), email.subject(), email.body());
    }

    /**
     * Not one transaction on purpose: every member gets their own, and the result says what happened to each.
     */
    public List<InvoiceSendResultDto> sendInvoices(InvoiceCreateRequestDto request, Long adminId) {
        // Checked before anything is sent: without payee and IBAN no invoice can be paid
        getConfiguredOrganisation();
        Member admin = getValidMemberBy(adminId);
        List<InvoiceSendResultDto> results = request.getMemberIds().stream()
                .distinct()
                .map(memberId -> invoiceMember(memberId, admin))
                .toList();
        log.info("Invoices sent: adminId={}, sent={}, failed={}, skipped={}", adminId,
                countWith(results, InvoiceSendStatusEnum.SENT), countWith(results, InvoiceSendStatusEnum.FAILED),
                countWith(results, InvoiceSendStatusEnum.SKIPPED));
        return results;
    }

    @Transactional(readOnly = true)
    public InvoicePageDto getInvoices(Long memberId, int page) {
        Slice<Invoice> invoices = invoiceRepository.findInvoices(memberId, PageRequest.of(page, PAGE_SIZE));
        return new InvoicePageDto(invoiceMapper.toInvoiceDtos(invoices.getContent()), page, invoices.hasNext());
    }

    /**
     * Same number, amount, due date and balance lines as the first attempt, to the member's current address (the usual
     * fix is a corrected email). Answers with the invoice also when the mail server fails again: then it is still FAILED
     * with the new error.
     */
    @Transactional
    public InvoiceDto resendInvoice(Long invoiceId, Long adminId) {
        Organisation organisation = getConfiguredOrganisation();
        Invoice invoice = getValidInvoiceForUpdateBy(invoiceId);
        validateNotSent(invoice);
        validateStillInDebt(invoice);
        invoice.setEmailTo(invoice.getMember().getEmail());
        send(invoice, organisation);
        log.info("Invoice resent: invoiceId={}, number={}, status={}, adminId={}", invoice.getId(), invoice.getNumber(),
                invoice.getStatus(), adminId);
        return invoiceMapper.toInvoiceDto(invoice);
    }

    // ---- candidates ----

    private static InvoiceCandidateDto toCandidateDto(Member member, Invoice latestInvoice, Set<Long> recentlyInvoiced) {
        InvoiceCandidateDto dto = new InvoiceCandidateDto();
        dto.setMemberId(member.getId());
        dto.setDisplayName(member.getDisplayName());
        dto.setEmail(member.getEmail());
        dto.setActive(member.getActive());
        dto.setBalanceCents(member.getBalanceCents());
        if (latestInvoice != null) {
            dto.setLastInvoiceAt(latestInvoice.getCreatedAt());
            dto.setLastInvoiceStatus(latestInvoice.getStatus());
        }
        dto.setDefaultSelected(!recentlyInvoiced.contains(member.getId()));
        return dto;
    }

    // ---- sending ----

    // An unexpected error rolls back only this member: nothing was saved, so the admin can safely send again
    private InvoiceSendResultDto invoiceMember(Long memberId, Member admin) {
        try {
            return transactionTemplate.execute(status -> createAndSend(memberId, admin));
        } catch (RuntimeException e) {
            log.error("Invoice not created: memberId={}", memberId, e);
            return skipped(memberId, InvoiceSkipReasonEnum.ERROR);
        }
    }

    // Runs in the member's own transaction; the organisation row lock is held until it commits
    private InvoiceSendResultDto createAndSend(Long memberId, Member admin) {
        Organisation organisation = getConfiguredOrganisationForUpdate();
        Optional<Member> member = memberRepository.findById(memberId);
        if (member.isEmpty()) {
            return skipped(memberId, InvoiceSkipReasonEnum.MEMBER_NOT_FOUND);
        }
        // Read with a query: a Member already loaded in this persistence context may hold an old balance
        long balanceCents = memberRepository.findBalanceCents(memberId);
        if (balanceCents >= 0) {
            return skipped(memberId, InvoiceSkipReasonEnum.NOT_IN_DEBT);
        }
        Invoice invoice = createInvoice(member.get(), balanceCents, admin);
        send(invoice, organisation);
        invoiceRepository.save(invoice);
        log.info("Invoice created: invoiceId={}, number={}, memberId={}, status={}", invoice.getId(), invoice.getNumber(),
                memberId, invoice.getStatus());
        return toSendResult(invoice);
    }

    private Invoice createInvoice(Member member, long balanceCents, Member admin) {
        OffsetDateTime now = now();
        LocalDate today = now.atZoneSameInstant(ClockConfig.APP_ZONE).toLocalDate();
        Invoice invoice = new Invoice();
        invoice.setNumber(nextNumber(today.getYear()));
        invoice.setMember(member);
        invoice.setEmailTo(member.getEmail());
        invoice.setAmountCents(-balanceCents);
        invoice.setDueDate(today.plusDays(properties.invoice().dueDays()));
        invoice.setSentBy(admin);
        invoice.setCreatedAt(now);
        return invoice;
    }

    private String nextNumber(int year) {
        int last = invoiceRepository.findLastNumberOfYear(year + "-%").orElse(0);
        return NUMBER_FORMAT.formatted(year, last + 1);
    }

    private void send(Invoice invoice, Organisation organisation) {
        try {
            mailer.send(composeEmail(invoice, organisation).toMailMessage());
            invoice.setStatus(InvoiceStatusEnum.SENT);
            invoice.setSentAt(now());
            invoice.setError(null);
        } catch (MailSendFailedException e) {
            invoice.setStatus(InvoiceStatusEnum.FAILED);
            invoice.setError(e.getReason());
            log.warn("Invoice email failed: number={}, memberId={}", invoice.getNumber(), invoice.getMember().getId());
        }
    }

    /**
     * Lines from the last invoice that reached the member before this one (at most 60 days back) up to the moment this
     * one was made. The closing balance is the debt; the opening balance is what the lines started from.
     */
    private InvoiceEmail composeEmail(Invoice invoice, Organisation organisation) {
        Long memberId = invoice.getMember().getId();
        OffsetDateTime periodEnd = invoice.getCreatedAt();
        OffsetDateTime periodStart = periodStartFor(memberId, periodEnd);
        List<AccountEntryDto> entries = accountEntryService.getMemberEntriesBetween(memberId, periodStart, periodEnd);
        long entriesTotal = entries.stream().mapToLong(AccountEntryDto::getAmountCents).sum();
        long openingBalanceCents = -invoice.getAmountCents() - entriesTotal;
        return new InvoiceEmail(invoice.getEmailTo(), invoice.getMember().getDisplayName(), invoice.getNumber(),
                invoice.getAmountCents(), invoice.getDueDate(), organisation.getName(), organisation.getIban(),
                periodStart, openingBalanceCents, entries, periodEnd);
    }

    private OffsetDateTime periodStartFor(Long memberId, OffsetDateTime periodEnd) {
        OffsetDateTime earliest = periodEnd.minus(MAX_LINES_PERIOD);
        return invoiceRepository.findLastSentCreatedAt(memberId, periodEnd)
                .filter(lastSent -> lastSent.isAfter(earliest))
                .orElse(earliest);
    }

    private InvoiceSendResultDto toSendResult(Invoice invoice) {
        InvoiceSendResultDto result = new InvoiceSendResultDto();
        result.setMemberId(invoice.getMember().getId());
        result.setStatus(invoice.getStatus() == InvoiceStatusEnum.SENT
                ? InvoiceSendStatusEnum.SENT : InvoiceSendStatusEnum.FAILED);
        result.setInvoice(invoiceMapper.toInvoiceDto(invoice));
        return result;
    }

    private static InvoiceSendResultDto skipped(Long memberId, InvoiceSkipReasonEnum reason) {
        InvoiceSendResultDto result = new InvoiceSendResultDto();
        result.setMemberId(memberId);
        result.setStatus(InvoiceSendStatusEnum.SKIPPED);
        result.setSkipReason(reason);
        return result;
    }

    private static long countWith(List<InvoiceSendResultDto> results, InvoiceSendStatusEnum status) {
        return results.stream().filter(result -> result.getStatus() == status).count();
    }

    // ---- resending ----

    private Invoice getValidInvoiceForUpdateBy(Long invoiceId) {
        return invoiceRepository.findForUpdate(invoiceId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private static void validateNotSent(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatusEnum.SENT) {
            throw new ForbiddenException(Error.INVOICE_ALREADY_SENT);
        }
    }

    // A member who has paid meanwhile must not get an old payment request
    private void validateStillInDebt(Invoice invoice) {
        if (memberRepository.findBalanceCents(invoice.getMember().getId()) >= 0) {
            throw new ForbiddenException(Error.MEMBER_NOT_IN_DEBT);
        }
    }

    // ---- shared ----

    private Organisation getConfiguredOrganisation() {
        return validateConfigured(organisationRepository.findById(Organisation.ID));
    }

    private Organisation getConfiguredOrganisationForUpdate() {
        return validateConfigured(organisationRepository.findForUpdate(Organisation.ID));
    }

    private static Organisation validateConfigured(Optional<Organisation> organisation) {
        return organisation
                .filter(found -> found.getName() != null && found.getIban() != null)
                .orElseThrow(() -> new DataNotFoundException(Error.PAYMENT_DETAILS_NOT_SET));
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
