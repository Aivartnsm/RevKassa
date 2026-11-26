package ee.revkassa.service;

import ee.revkassa.controller.admin.member.dto.AdjustmentCreateRequestDto;
import ee.revkassa.controller.admin.member.dto.AdminMemberDto;
import ee.revkassa.controller.admin.member.dto.MemberCreateRequestDto;
import ee.revkassa.controller.admin.member.dto.MemberCreatedDto;
import ee.revkassa.controller.admin.member.dto.MemberPinDto;
import ee.revkassa.controller.admin.member.dto.MemberUpdateRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberMapper;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import ee.revkassa.persistence.passwordtoken.PasswordTokenPurposeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Admin actions on members. Members are never deleted (the ledger refers to them): an admin deactivates one instead.
 * The admin never sees or sets a password: a new member gets an invite link by email. A PIN is shown to the admin
 * once, when it is made, and never logged. The balance changes only through the ledger (adjustments here).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final MembershipLevelRepository membershipLevelRepository;
    private final PinService pinService;
    private final PasswordLinkService passwordLinkService;
    private final AccountService accountService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AdminMemberDto> getMembers() {
        return memberMapper.toAdminMemberDtos(memberRepository.findAllMembers());
    }

    /**
     * Saves the member and emails the invite in one transaction: if the mail server fails, the member is not created
     * (503) and the admin can simply try again.
     */
    @Transactional
    public MemberCreatedDto createMember(MemberCreateRequestDto request, Long adminId) {
        Member admin = getValidMemberBy(adminId);
        validateEmailFree(request.getEmail(), null);
        String pin = pinService.generateUniquePin();
        MembershipLevel level = request.getLevelId() == null ? getFirstActiveLevel() : getValidActiveLevelBy(request.getLevelId());
        Member member = memberRepository.save(createMember(request, pin, level));
        passwordLinkService.send(member, PasswordTokenPurposeEnum.INVITE, admin);
        log.info("Member created: memberId={}, role={}, adminId={}", member.getId(), member.getRole(), adminId);
        return new MemberCreatedDto(memberMapper.toAdminMemberDto(member), pin);
    }

    @Transactional
    public AdminMemberDto updateMember(Long memberId, MemberUpdateRequestDto request, Long adminId) {
        Member member = getValidMemberBy(memberId);
        validateVersion(member, request.getVersion());
        validateEmailFree(request.getEmail(), memberId);
        validateAnActiveAdminRemains(member, request);
        MembershipLevel level = getValidLevelForMemberBy(request.getLevelId(), member);
        boolean emailChanged = !member.getEmail().equalsIgnoreCase(request.getEmail());
        memberMapper.updateMember(request, member);
        member.setDisplayName(request.getDisplayName().strip());
        member.setLevel(level);
        if (emailChanged) {
            // A link sent to the old address must not set the password of this account any more
            passwordLinkService.cancelOpenLinks(member);
        }
        memberRepository.flush();
        log.info("Member updated: memberId={}, role={}, active={}, feeExempt={}, levelId={}, emailChanged={}, adminId={}",
                memberId, member.getRole(), member.getActive(), member.getFeeExempt(), level.getId(), emailChanged, adminId);
        return memberMapper.toAdminMemberDto(member);
    }

    @Transactional
    public void sendInvite(Long memberId, Long adminId) {
        Member member = getValidMemberBy(memberId);
        validateActive(member);
        passwordLinkService.send(member, PasswordTokenPurposeEnum.INVITE, getValidMemberBy(adminId));
    }

    /**
     * The old PIN stops working at once. The new one is returned to show to the admin this one time.
     */
    @Transactional
    public MemberPinDto resetPin(Long memberId, Long adminId) {
        Member member = getValidMemberBy(memberId);
        String pin = pinService.generateUniquePin();
        member.setPinHmac(pinService.hash(pin));
        member.setPinChangedAt(OffsetDateTime.now(clock));
        memberRepository.flush();
        log.info("PIN reset by admin: memberId={}, adminId={}", memberId, adminId);
        return new MemberPinDto(pin);
    }

    // Also for a deactivated member: a former member's debt may still need correcting
    @Transactional
    public AdminMemberDto addAdjustment(Long memberId, AdjustmentCreateRequestDto request, Long adminId) {
        Member member = getValidMemberForUpdateBy(memberId);
        Member admin = getValidMemberBy(adminId);
        long balanceCents = accountService.post(
                AccountPosting.adjustment(member, request.getAmountCents(), request.getNote(), admin));
        log.info("Balance adjusted: memberId={}, adminId={}", memberId, adminId);
        AdminMemberDto dto = memberMapper.toAdminMemberDto(member);
        dto.setBalanceCents(balanceCents);
        return dto;
    }

    // ---- helpers ----

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Case does not matter: the email is the login name and the unique index is on lower(email)
    private void validateEmailFree(String email, Long ownMemberId) {
        memberRepository.findByEmail(email)
                .filter(other -> !other.getId().equals(ownMemberId))
                .ifPresent(other -> {
                    throw new ForbiddenException(Error.EMAIL_TAKEN);
                });
    }

    private Member createMember(MemberCreateRequestDto request, String pin, MembershipLevel level) {
        Member member = memberMapper.toMember(request);
        member.setDisplayName(request.getDisplayName().strip());
        member.setPinHmac(pinService.hash(pin));
        member.setBalanceCents(0L);
        member.setActive(true);
        member.setLevel(level);
        OffsetDateTime now = OffsetDateTime.now(clock);
        member.setPinChangedAt(now);
        member.setCreatedAt(now);
        return member;
    }

    // MembershipLevelService keeps at least one level active
    private MembershipLevel getFirstActiveLevel() {
        return membershipLevelRepository.findAllActive().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active membership level: run docs/database/2_create.sql"));
    }

    private MembershipLevel getValidActiveLevelBy(Long levelId) {
        MembershipLevel level = getValidLevelBy(levelId);
        if (!level.getActive()) {
            throw new ForbiddenException(Error.LEVEL_NOT_ACTIVE);
        }
        return level;
    }

    // A deactivated level stays allowed for the members who already have it, so their other fields can still be edited
    private MembershipLevel getValidLevelForMemberBy(Long levelId, Member member) {
        if (levelId.equals(member.getLevel().getId())) {
            return member.getLevel();
        }
        return getValidActiveLevelBy(levelId);
    }

    private MembershipLevel getValidLevelBy(Long levelId) {
        return membershipLevelRepository.findById(levelId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // JPA checks the version it loaded itself; this compares the version the admin's form was loaded with
    private static void validateVersion(Member member, Long requestVersion) {
        if (!member.getVersion().equals(requestVersion)) {
            throw new ObjectOptimisticLockingFailureException(Member.class, member.getId());
        }
    }

    // Without an active admin nobody could manage the app any more (also when an admin demotes themselves)
    private void validateAnActiveAdminRemains(Member member, MemberUpdateRequestDto request) {
        boolean losesAdminRights = request.getRole() != MemberRoleEnum.ADMIN || !request.getActive();
        if (!isActiveAdmin(member) || !losesAdminRights) {
            return;
        }
        boolean otherActiveAdminExists = memberRepository.findActiveAdminsForUpdate().stream()
                .anyMatch(admin -> !admin.getId().equals(member.getId()));
        if (!otherActiveAdminExists) {
            throw new ForbiddenException(Error.LAST_ADMIN);
        }
    }

    private static boolean isActiveAdmin(Member member) {
        return member.getRole() == MemberRoleEnum.ADMIN && member.getActive();
    }

    // An inactive member could not use the link anyway
    private static void validateActive(Member member) {
        if (!member.getActive()) {
            throw new ForbiddenException(Error.ACCOUNT_NOT_ACTIVE);
        }
    }

    // The same lock as purchases and payments, so a correction never races them on this member's balance
    private Member getValidMemberForUpdateBy(Long memberId) {
        return memberRepository.findForUpdate(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }
}
