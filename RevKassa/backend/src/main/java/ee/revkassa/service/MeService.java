package ee.revkassa.service;

import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.security.LockoutKey;
import ee.revkassa.infrastructure.security.LoginLockoutService;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.infrastructure.security.PasswordService;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberMapper;
import ee.revkassa.persistence.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * The member always comes from the session, never from the request, so nobody can read or change another
 * member's data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final PasswordService passwordService;
    private final LoginLockoutService loginLockoutService;
    private final PinService pinService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MemberSessionDto getMe(MemberPrincipal principal) {
        Member member = getValidMemberBy(principal.memberId());
        return memberMapper.toMemberSessionDto(member, principal.loginMethod());
    }

    /**
     * Wrong current passwords are limited per member like logins, so an open session (e.g. on a tablet left
     * logged in) cannot be used to guess the password. Other sessions of the member stay valid.
     */
    @Transactional
    public void changePassword(MemberPrincipal principal, String currentPassword, String newPassword) {
        LockoutKey lockoutKey = LockoutKey.forPasswordChange(principal.memberId());
        loginLockoutService.ensureNotLocked(lockoutKey);
        Member member = getValidMemberBy(principal.memberId());
        validateCurrentPassword(member, currentPassword, lockoutKey);
        loginLockoutService.registerSuccess(lockoutKey);
        member.setPasswordHash(passwordService.encode(newPassword));
        log.info("Password changed: memberId={}", member.getId());
    }

    /**
     * A PIN alone logs a member in on the bar tablet, so "this PIN is taken" would reveal a working PIN of someone
     * else: it counts as a wrong attempt just like a wrong current PIN, and the counter is cleared only when the
     * change succeeds. The answer never says whose PIN it is.
     */
    @Transactional
    public void changePin(MemberPrincipal principal, String currentPin, String newPin) {
        LockoutKey lockoutKey = LockoutKey.forPinChange(principal.memberId());
        loginLockoutService.ensureNotLocked(lockoutKey);
        Member member = getValidMemberBy(principal.memberId());
        validateCurrentPin(member, currentPin, lockoutKey);
        validateNewPin(member, currentPin, newPin, lockoutKey);
        loginLockoutService.registerSuccess(lockoutKey);
        member.setPinHmac(pinService.hash(newPin));
        member.setPinChangedAt(OffsetDateTime.now(clock));
        log.info("PIN changed: memberId={}", member.getId());
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // A member who has no password yet (PIN only) cannot match either: they set one with a link instead
    private void validateCurrentPassword(Member member, String currentPassword, LockoutKey lockoutKey) {
        if (!passwordService.matches(currentPassword, member.getPasswordHash())) {
            log.warn("Password change refused, wrong current password: memberId={}", member.getId());
            throw loginLockoutService.registerFailure(lockoutKey);
        }
    }

    private void validateCurrentPin(Member member, String currentPin, LockoutKey lockoutKey) {
        if (!pinService.matches(currentPin, member.getPinHmac())) {
            log.warn("PIN change refused, wrong current PIN: memberId={}", member.getId());
            throw loginLockoutService.registerFailure(lockoutKey);
        }
    }

    // The current PIN is already proven here, so "same as current" reveals nothing and is not counted
    private void validateNewPin(Member member, String currentPin, String newPin, LockoutKey lockoutKey) {
        if (newPin.equals(currentPin)) {
            throw new ForbiddenException(Error.NEW_PIN_SAME_AS_CURRENT);
        }
        if (pinService.isTaken(newPin)) {
            log.warn("PIN change refused, new PIN not available: memberId={}", member.getId());
            throw loginLockoutService.registerFailure(lockoutKey, Error.PIN_NOT_AVAILABLE);
        }
    }
}
