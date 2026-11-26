package ee.revkassa.service;

import ee.revkassa.controller.admin.membershipfee.dto.ChargeWaiveRequestDto;
import ee.revkassa.controller.admin.membershipfee.dto.MembershipChargeDto;
import ee.revkassa.controller.admin.membershipfee.dto.MonthChargesDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.membershipcharge.MembershipCharge;
import ee.revkassa.persistence.membershipcharge.MembershipChargeMapper;
import ee.revkassa.persistence.membershipcharge.MembershipChargeRepository;
import ee.revkassa.persistence.membershipcharge.MembershipChargeStatusEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Charges the monthly fee: every member who is active, not exempt and joined before the month gets their level's fee
 * for that month (feeFor(level, month), never a later fee), as a membership_charge row and one MEMBERSHIP_FEE ledger
 * row. A fee of 0 writes nothing. Running again only fills in members still missing, so a missed run is made up later
 * in the month and two runs never charge twice (member row lock + unique (member_id, month)).
 * An admin may waive a charged fee with a note (B24): the charge stays as WAIVED, so it is never charged again, and a
 * MEMBERSHIP_FEE_WAIVER row gives the whole amount back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipChargeService {

    private final MembershipChargeRepository membershipChargeRepository;
    private final MembershipChargeMapper membershipChargeMapper;
    private final MemberRepository memberRepository;
    private final MembershipLevelRepository membershipLevelRepository;
    private final MembershipFeeService membershipFeeService;
    private final AccountService accountService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public record ChargeRunResult(int charged, int skipped, int failed) {
    }

    /**
     * One transaction per member, so one failing member does not stop the others; failures are logged and the next
     * run tries them again.
     */
    public ChargeRunResult chargeMonth(YearMonth month) {
        List<Long> memberIds = membershipChargeRepository.findChargeableMemberIds(month.atDay(1), startOf(month));
        int charged = 0;
        int skipped = 0;
        int failed = 0;
        for (Long memberId : memberIds) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> charge(memberId, month)))) {
                    charged++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("Membership fee not charged: memberId={}, month={}", memberId, month, e);
            }
        }
        log.info("Monthly fees run: month={}, charged={}, skipped={}, failed={}", month, charged, skipped, failed);
        return new ChargeRunResult(charged, skipped, failed);
    }

    /**
     * @return true if a fee was charged; false if the member is not to be charged (any more) or the fee is 0
     */
    @Transactional
    public boolean chargeMember(Long memberId, YearMonth month) {
        return charge(memberId, month);
    }

    // Runs in the caller's transaction: chargeMember's, or one per member in chargeMonth
    private boolean charge(Long memberId, YearMonth month) {
        Member member = getValidMemberForUpdateBy(memberId);
        if (!isChargeable(member, month) || membershipChargeRepository.isCharged(memberId, month.atDay(1))) {
            return false;
        }
        MembershipLevel level = getValidLevelForShareBy(member.getLevel().getId());
        long amountCents = membershipFeeService.feeFor(level.getId(), month);
        if (amountCents == 0) {
            return false;
        }
        MembershipCharge charge = membershipChargeRepository.save(createCharge(member, month, level, amountCents));
        accountService.post(AccountPosting.membershipFee(charge));
        log.info("Membership fee charged: chargeId={}, memberId={}, month={}", charge.getId(), memberId, month);
        return true;
    }

    /**
     * @param month null = the current month
     */
    @Transactional(readOnly = true)
    public MonthChargesDto getMonthCharges(YearMonth month) {
        YearMonth shownMonth = month != null ? month : YearMonth.now(clock);
        List<MembershipCharge> charges = membershipChargeRepository.findMonthCharges(shownMonth.atDay(1));
        MonthChargesDto dto = new MonthChargesDto();
        dto.setMonth(shownMonth);
        dto.setCharges(membershipChargeMapper.toMembershipChargeDtos(charges));
        dto.setChargedCount(countWith(charges, MembershipChargeStatusEnum.CHARGED));
        dto.setChargedTotalCents(totalWith(charges, MembershipChargeStatusEnum.CHARGED));
        dto.setWaivedCount(countWith(charges, MembershipChargeStatusEnum.WAIVED));
        dto.setWaivedTotalCents(totalWith(charges, MembershipChargeStatusEnum.WAIVED));
        return dto;
    }

    /**
     * Any month, also for an inactive member. Final: an undo would be a manual adjustment.
     */
    @Transactional
    public MembershipChargeDto waiveCharge(Long chargeId, ChargeWaiveRequestDto request, Long adminId) {
        Member member = getValidMemberForUpdateBy(getValidMemberIdBy(chargeId));
        Member admin = getValidMemberBy(adminId);
        MembershipCharge charge = getValidChargedChargeBy(chargeId);
        String note = request.getNote().strip();
        markWaived(charge, admin, note);
        accountService.post(AccountPosting.membershipFeeWaiver(charge, note, admin));
        log.info("Membership fee waived: chargeId={}, memberId={}, adminId={}", chargeId, member.getId(), adminId);
        return membershipChargeMapper.toMembershipChargeDto(charge);
    }

    // ---- helpers ----

    // The month starts at midnight in the club's time zone
    private static OffsetDateTime startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ClockConfig.APP_ZONE).toOffsetDateTime();
    }

    // Lock order as everywhere: the member's row first
    private Member getValidMemberForUpdateBy(Long memberId) {
        return memberRepository.findForUpdate(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Checked again under the lock: the member may have changed since the list was read
    private static boolean isChargeable(Member member, YearMonth month) {
        return member.getActive() && !member.getFeeExempt() && member.getCreatedAt().isBefore(startOf(month));
    }

    private MembershipLevel getValidLevelForShareBy(Long levelId) {
        return membershipLevelRepository.findForShare(levelId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private static long countWith(List<MembershipCharge> charges, MembershipChargeStatusEnum status) {
        return charges.stream().filter(charge -> charge.getStatus() == status).count();
    }

    private static long totalWith(List<MembershipCharge> charges, MembershipChargeStatusEnum status) {
        return charges.stream()
                .filter(charge -> charge.getStatus() == status)
                .mapToLong(MembershipCharge::getAmountCents)
                .sum();
    }

    private Long getValidMemberIdBy(Long chargeId) {
        return membershipChargeRepository.findMemberId(chargeId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Read after the member's row is locked: a parallel waiver of the same charge has finished by now
    private MembershipCharge getValidChargedChargeBy(Long chargeId) {
        MembershipCharge charge = membershipChargeRepository.findById(chargeId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
        if (charge.getStatus() != MembershipChargeStatusEnum.CHARGED) {
            throw new ForbiddenException(Error.FEE_ALREADY_WAIVED);
        }
        return charge;
    }

    private void markWaived(MembershipCharge charge, Member admin, String note) {
        charge.setStatus(MembershipChargeStatusEnum.WAIVED);
        charge.setWaivedBy(admin);
        charge.setWaivedAt(OffsetDateTime.now(clock));
        charge.setWaiveNote(note);
    }

    private MembershipCharge createCharge(Member member, YearMonth month, MembershipLevel level, long amountCents) {
        MembershipCharge charge = new MembershipCharge();
        charge.setMember(member);
        charge.setMonth(month.atDay(1));
        charge.setLevel(level);
        charge.setAmountCents(amountCents);
        charge.setStatus(MembershipChargeStatusEnum.CHARGED);
        charge.setCreatedAt(OffsetDateTime.now(clock));
        return charge;
    }
}
