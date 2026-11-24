package ee.revkassa.service;

import ee.revkassa.controller.admin.device.dto.DeviceDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import ee.revkassa.persistence.trusteddevice.TrustedDeviceMapper;
import ee.revkassa.persistence.trusteddevice.TrustedDeviceRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Admin management of trusted devices (the bar tablet). Registering trusts the browser that sends the request:
 * it gets a new random token in the REVKASSA_DEVICE cookie, the database keeps only the token's hash.
 * Rows are never deleted; revoking keeps the history. Tokens are never logged or returned in a body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustedDeviceService {

    private final DeviceService deviceService;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final TrustedDeviceMapper trustedDeviceMapper;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DeviceDto> findAllDevices() {
        return trustedDeviceMapper.toDeviceDtos(trustedDeviceRepository.findAllForAdmin());
    }

    @Transactional
    public DeviceDto registerThisDevice(String name, String currentDeviceToken, Long adminId, HttpServletResponse response) {
        validateNotTrustedYet(currentDeviceToken);
        String deviceToken = deviceService.createToken();
        TrustedDevice device = trustedDeviceRepository.save(createDevice(name, deviceToken, adminId));
        response.addHeader(HttpHeaders.SET_COOKIE, deviceService.createDeviceCookie(deviceToken).toString());
        log.info("Trusted device registered: deviceId={}, adminId={}", device.getId(), adminId);
        return trustedDeviceMapper.toDeviceDto(device);
    }

    @Transactional
    public DeviceDto revokeDevice(Long deviceId, Long adminId) {
        TrustedDevice device = getValidDeviceForUpdateBy(deviceId);
        validateNotRevoked(device);
        device.setRevokedBy(getValidMemberBy(adminId));
        device.setRevokedAt(OffsetDateTime.now(clock));
        log.info("Trusted device revoked: deviceId={}, adminId={}", deviceId, adminId);
        return trustedDeviceMapper.toDeviceDto(device);
    }

    // A second registration would replace this browser's cookie and leave the old row trusted with nobody holding it
    private void validateNotTrustedYet(String currentDeviceToken) {
        if (deviceService.findTrusted(currentDeviceToken).isPresent()) {
            throw new ForbiddenException(Error.DEVICE_ALREADY_TRUSTED);
        }
    }

    private TrustedDevice createDevice(String name, String deviceToken, Long adminId) {
        TrustedDevice device = new TrustedDevice();
        device.setName(name.strip());
        device.setTokenHash(deviceService.hash(deviceToken));
        device.setCreatedBy(getValidMemberBy(adminId));
        device.setCreatedAt(OffsetDateTime.now(clock));
        return device;
    }

    private TrustedDevice getValidDeviceForUpdateBy(Long deviceId) {
        return trustedDeviceRepository.findForUpdate(deviceId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private static void validateNotRevoked(TrustedDevice device) {
        if (device.getRevokedAt() != null) {
            throw new ForbiddenException(Error.DEVICE_ALREADY_REVOKED);
        }
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }
}
