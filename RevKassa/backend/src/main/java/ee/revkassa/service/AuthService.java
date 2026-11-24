package ee.revkassa.service;

import ee.revkassa.controller.auth.dto.DeviceStatusDto;
import ee.revkassa.controller.auth.dto.LoginRequestDto;
import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.mail.MailSendFailedException;
import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.infrastructure.security.LockoutKey;
import ee.revkassa.infrastructure.security.LoginLockoutService;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import ee.revkassa.infrastructure.security.LoginSessionService;
import ee.revkassa.infrastructure.security.PasswordResetLimiter;
import ee.revkassa.infrastructure.security.PasswordService;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberMapper;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.passwordtoken.PasswordTokenPurposeEnum;
import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Login rules: PIN only on a trusted device (member session, 1 min), email + password anywhere (30 min, admin rights
 * for ADMIN). Wrong attempts are limited per device (PIN) and per client IP (password), see LoginLockoutService.
 * PINs, passwords and device tokens are never logged; log lines carry ids and the client IP only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final DeviceService deviceService;
    private final PinService pinService;
    private final PasswordService passwordService;
    private final LoginLockoutService loginLockoutService;
    private final LoginSessionService loginSessionService;
    private final PasswordResetLimiter passwordResetLimiter;
    private final PasswordLinkService passwordLinkService;
    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final Clock clock;

    public DeviceStatusDto getDeviceStatus(String deviceToken) {
        return deviceService.findTrusted(deviceToken)
                .map(device -> new DeviceStatusDto(true, device.getName()))
                .orElseGet(() -> new DeviceStatusDto(false, null));
    }

    @Transactional
    public MemberSessionDto loginWithPin(String pin, String deviceToken,
                                         HttpServletRequest request, HttpServletResponse response) {
        TrustedDevice device = getTrustedDevice(deviceToken, request);
        LockoutKey lockoutKey = LockoutKey.forDevice(device.getId());
        loginLockoutService.ensureNotLocked(lockoutKey);
        Member member = getMemberByPin(pin, device, lockoutKey, request);
        validateActive(member);
        loginLockoutService.registerSuccess(lockoutKey);
        markDeviceUsed(device, deviceToken, response);
        loginSessionService.start(member, LoginMethodEnum.PIN, request, response);
        log.info("PIN login: memberId={}, deviceId={}, ip={}", member.getId(), device.getId(), request.getRemoteAddr());
        return memberMapper.toMemberSessionDto(member, LoginMethodEnum.PIN);
    }

    @Transactional(readOnly = true)
    public MemberSessionDto loginWithPassword(LoginRequestDto loginRequest,
                                              HttpServletRequest request, HttpServletResponse response) {
        LockoutKey lockoutKey = LockoutKey.forIp(request.getRemoteAddr());
        loginLockoutService.ensureNotLocked(lockoutKey);
        Member member = getMemberByCredentials(loginRequest, lockoutKey, request);
        validateActive(member);
        loginLockoutService.registerSuccess(lockoutKey);
        loginSessionService.start(member, LoginMethodEnum.PASSWORD, request, response);
        log.info("Password login: memberId={}, ip={}", member.getId(), request.getRemoteAddr());
        return memberMapper.toMemberSessionDto(member, LoginMethodEnum.PASSWORD);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        loginSessionService.end(request, response);
    }

    /**
     * Always ends the same way for the caller (204), whether the email exists, is inactive, hit the per-address limit
     * or the mail server failed: the answer must not reveal which emails belong to members.
     * Not one transaction on purpose: a failed email must not turn into an error response.
     */
    public void requestPasswordReset(String email, HttpServletRequest request) {
        passwordResetLimiter.acquireForIp(request.getRemoteAddr());
        Optional<Member> member = memberRepository.findByEmail(email.strip());
        if (member.isEmpty() || !member.get().getActive()) {
            log.info("Password reset requested for no active member: ip={}", request.getRemoteAddr());
            return;
        }
        if (!passwordResetLimiter.tryAcquireForEmail(member.get().getEmail())) {
            log.warn("Password reset email limit reached: memberId={}, ip={}", member.get().getId(), request.getRemoteAddr());
            return;
        }
        sendResetLink(member.get());
    }

    public void setPassword(String token, String newPassword) {
        passwordLinkService.setPassword(token, newPassword);
    }

    // Checked before the PIN: from an untrusted device PINs cannot be tried at all
    private TrustedDevice getTrustedDevice(String deviceToken, HttpServletRequest request) {
        Optional<TrustedDevice> device = deviceService.findTrusted(deviceToken);
        if (device.isEmpty()) {
            log.warn("PIN login refused on untrusted device: ip={}", request.getRemoteAddr());
            throw new ForbiddenException(Error.DEVICE_NOT_TRUSTED);
        }
        return device.get();
    }

    private Member getMemberByPin(String pin, TrustedDevice device, LockoutKey lockoutKey, HttpServletRequest request) {
        Optional<Member> member = pinService.findByPin(pin);
        if (member.isEmpty()) {
            log.warn("Wrong PIN: deviceId={}, ip={}", device.getId(), request.getRemoteAddr());
            throw loginLockoutService.registerFailure(lockoutKey);
        }
        return member.get();
    }

    // Unknown email, password not set yet and wrong password give the same answer: it must not reveal which emails exist
    private Member getMemberByCredentials(LoginRequestDto loginRequest, LockoutKey lockoutKey, HttpServletRequest request) {
        Optional<Member> member = memberRepository.findByEmail(loginRequest.getEmail());
        String passwordHash = member.map(Member::getPasswordHash).orElse(null);
        if (!passwordService.matches(loginRequest.getPassword(), passwordHash)) {
            log.warn("Password login failed: ip={}", request.getRemoteAddr());
            throw loginLockoutService.registerFailure(lockoutKey);
        }
        return member.get();
    }

    private void sendResetLink(Member member) {
        try {
            passwordLinkService.send(member, PasswordTokenPurposeEnum.RESET, null);
        } catch (MailSendFailedException e) {
            log.error("Password reset email failed: memberId={}, reason={}", member.getId(), e.getReason());
        }
    }

    private static void validateActive(Member member) {
        if (!member.getActive()) {
            log.info("Login refused, member not active: memberId={}", member.getId());
            throw new ForbiddenException(Error.ACCOUNT_NOT_ACTIVE);
        }
    }

    // Every PIN login renews the device cookie, so a tablet in daily use never falls out of trust
    private void markDeviceUsed(TrustedDevice device, String deviceToken, HttpServletResponse response) {
        device.setLastUsedAt(OffsetDateTime.now(clock));
        response.addHeader(HttpHeaders.SET_COOKIE, deviceService.createDeviceCookie(deviceToken).toString());
    }
}
