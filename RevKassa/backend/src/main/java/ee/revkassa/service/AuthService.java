package ee.revkassa.service;

import ee.revkassa.controller.auth.dto.DeviceStatusDto;
import ee.revkassa.infrastructure.security.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final DeviceService deviceService;

    public DeviceStatusDto getDeviceStatus(String deviceToken) {
        return deviceService.findTrusted(deviceToken)
                .map(device -> new DeviceStatusDto(true, device.getName()))
                .orElseGet(() -> new DeviceStatusDto(false, null));
    }
}
