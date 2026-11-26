package ee.revkassa.service;

import ee.revkassa.controller.admin.membershipfee.dto.LevelFeeRequestDto;
import ee.revkassa.controller.admin.membershipfee.dto.LevelFeesDto;
import ee.revkassa.controller.admin.membershipfee.dto.MembershipFeesCreateRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.membershipfee.MembershipFee;
import ee.revkassa.persistence.membershipfee.MembershipFeeMapper;
import ee.revkassa.persistence.membershipfee.MembershipFeeRepository;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monthly fees per membership level, kept as a history: a row is a level's fee from its month on, until the level's
 * next row. When a month is charged (B23), each member's fee is copied to that month's charge, so a later fee change
 * never reprices months already charged: a member who has not paid owes the old fees plus the new ones.
 * Hence a fee can be set or corrected only for a month that has not been charged for that level yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipFeeService {

    private final MembershipFeeRepository membershipFeeRepository;
    private final MembershipFeeMapper membershipFeeMapper;
    private final MembershipLevelRepository membershipLevelRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LevelFeesDto> getFees() {
        YearMonth currentMonth = YearMonth.now(clock);
        Map<Long, List<MembershipFee>> feesByLevelId = membershipFeeRepository.findAllFees().stream()
                .collect(Collectors.groupingBy(fee -> fee.getLevel().getId()));
        return membershipLevelRepository.findAllLevels().stream()
                .map(level -> toLevelFeesDto(level, feesByLevelId.getOrDefault(level.getId(), List.of()), currentMonth))
                .toList();
    }

    /**
     * Saves all the listed levels' fees or none. Saving a month again for a level replaces that row, as long as the
     * month has not been charged for the level.
     */
    @Transactional
    public List<LevelFeesDto> saveFees(MembershipFeesCreateRequestDto request, Long adminId) {
        YearMonth month = request.getValidFrom();
        validateNotPast(month);
        Member admin = getValidMemberBy(adminId);
        Map<Long, MembershipLevel> levels = getValidLevelsForUpdateBy(request.getFees());
        LocalDate validFrom = month.atDay(1);
        levels.values().forEach(level -> validateNotCharged(level, validFrom));
        OffsetDateTime now = OffsetDateTime.now(clock);
        request.getFees().forEach(levelFee ->
                saveFee(levels.get(levelFee.getLevelId()), validFrom, levelFee.getAmountCents(), admin, now));
        return getFees();
    }

    /**
     * The level's fee for this month: its latest row starting in or before the month, 0 if there is none.
     * The monthly fee job (B23) charges this amount.
     */
    @Transactional(readOnly = true)
    public long feeFor(Long levelId, YearMonth month) {
        return membershipFeeRepository.findAmountFor(levelId, month.atDay(1)).orElse(0L);
    }

    // ---- helpers ----

    // fees: newest month first
    private LevelFeesDto toLevelFeesDto(MembershipLevel level, List<MembershipFee> fees, YearMonth currentMonth) {
        LevelFeesDto dto = new LevelFeesDto();
        dto.setLevelId(level.getId());
        dto.setName(level.getName());
        dto.setActive(level.getActive());
        dto.setCurrentAmountCents(fees.stream()
                .filter(fee -> !isAfter(fee, currentMonth))
                .findFirst()
                .map(MembershipFee::getAmountCents)
                .orElse(0L));
        dto.setUpcoming(fees.stream()
                .filter(fee -> isAfter(fee, currentMonth))
                .reduce((later, earlier) -> earlier)
                .map(membershipFeeMapper::toMembershipFeeDto)
                .orElse(null));
        dto.setHistory(membershipFeeMapper.toMembershipFeeDtos(fees));
        return dto;
    }

    private static boolean isAfter(MembershipFee fee, YearMonth month) {
        return YearMonth.from(fee.getValidFrom()).isAfter(month);
    }

    private void validateNotPast(YearMonth month) {
        if (month.isBefore(YearMonth.now(clock))) {
            throw new ForbiddenException(Error.FEE_MONTH_PASSED);
        }
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Locked, so a parallel save for the same level waits, and so does the monthly fee job reading its fee
    private Map<Long, MembershipLevel> getValidLevelsForUpdateBy(List<LevelFeeRequestDto> levelFees) {
        List<Long> levelIds = levelFees.stream().map(LevelFeeRequestDto::getLevelId).toList();
        Map<Long, MembershipLevel> levels = membershipLevelRepository.findAllForUpdate(levelIds).stream()
                .collect(Collectors.toMap(MembershipLevel::getId, Function.identity()));
        if (levels.size() != levelIds.size()) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        return levels;
    }

    // Only the current month can have been charged already; its charges keep the amount they were made with
    private void validateNotCharged(MembershipLevel level, LocalDate validFrom) {
        if (membershipFeeRepository.isMonthCharged(level.getId(), validFrom)) {
            throw new ForbiddenException(Error.FEE_MONTH_ALREADY_CHARGED);
        }
    }

    private void saveFee(MembershipLevel level, LocalDate validFrom, long amountCents, Member admin, OffsetDateTime now) {
        MembershipFee fee = membershipFeeRepository.findByLevelAndMonth(level.getId(), validFrom)
                .orElseGet(() -> createFee(level, validFrom));
        Long oldAmountCents = fee.getAmountCents();
        fee.setAmountCents(amountCents);
        fee.setCreatedBy(admin);
        fee.setCreatedAt(now);
        membershipFeeRepository.save(fee);
        log.info("Membership fee set: levelId={}, validFrom={}, amountCents={}->{}, adminId={}",
                level.getId(), validFrom, oldAmountCents, amountCents, admin.getId());
    }

    private static MembershipFee createFee(MembershipLevel level, LocalDate validFrom) {
        MembershipFee fee = new MembershipFee();
        fee.setLevel(level);
        fee.setValidFrom(validFrom);
        return fee;
    }
}
