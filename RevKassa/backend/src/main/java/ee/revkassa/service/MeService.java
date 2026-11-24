package ee.revkassa.service;

import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.security.LockoutKey;
import ee.revkassa.infrastructure.security.LoginLockoutService;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.infrastructure.security.PasswordService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberMapper;
import ee.revkassa.persistence.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
