package ee.revkassa.service;

import ee.revkassa.controller.auth.dto.DeviceStatusDto;
import ee.revkassa.controller.auth.dto.LoginRequestDto;
import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import ee.revkassa.infrastructure.security.LoginSessionService;
import ee.revkassa.infrastructure.security.PasswordService;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberMapper;
import ee.revkassa.persistence.member.MemberRepository;
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
 * for ADMIN). PINs, passwords and device tokens are never logged; log lines carry ids and the client IP only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final DeviceService deviceService;
    private final PinService pinService;
    private final PasswordService passwordService;
    private final LoginSessionService loginSessionService;
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
        Member member = getMemberByPin(pin, device, request);
        validateActive(member);
        markDeviceUsed(device, deviceToken, response);
        loginSessionService.start(member, LoginMethodEnum.PIN, request, response);
        log.info("PIN login: memberId={}, deviceId={}, ip={}", member.getId(), device.getId(), request.getRemoteAddr());
        return memberMapper.toMemberSessionDto(member, LoginMethodEnum.PIN);
    }

    @Transactional(readOnly = true)
    public MemberSessionDto loginWithPassword(LoginRequestDto loginRequest,
                                              HttpServletRequest request, HttpServletResponse response) {
        Member member = getMemberByCredentials(loginRequest, request);
        validateActive(member);
        loginSessionService.start(member, LoginMethodEnum.PASSWORD, request, response);
        log.info("Password login: memberId={}, ip={}", member.getId(), request.getRemoteAddr());
        return memberMapper.toMemberSessionDto(member, LoginMethodEnum.PASSWORD);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        loginSessionService.end(request, response);
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

    private Member getMemberByPin(String pin, TrustedDevice device, HttpServletRequest request) {
        Optional<Member> member = pinService.findByPin(pin);
        if (member.isEmpty()) {
            log.warn("Wrong PIN: deviceId={}, ip={}", device.getId(), request.getRemoteAddr());
            throw new ForbiddenException(Error.WRONG_PIN);
        }
        return member.get();
    }

    // Unknown email, password not set yet and wrong password give the same answer: it must not reveal which emails exist
    private Member getMemberByCredentials(LoginRequestDto loginRequest, HttpServletRequest request) {
        Optional<Member> member = memberRepository.findByEmail(loginRequest.getEmail());
        String passwordHash = member.map(Member::getPasswordHash).orElse(null);
        if (!passwordService.matches(loginRequest.getPassword(), passwordHash)) {
            log.warn("Password login failed: ip={}", request.getRemoteAddr());
            throw new ForbiddenException(Error.WRONG_EMAIL_OR_PASSWORD);
        }
        return member.get();
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
