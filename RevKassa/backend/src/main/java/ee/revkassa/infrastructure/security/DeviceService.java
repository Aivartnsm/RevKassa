package ee.revkassa.infrastructure.security;

import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import ee.revkassa.persistence.trusteddevice.TrustedDeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * PIN login is allowed only on devices an admin has registered (the bar tablet). Such a device holds a random
 * token in the REVKASSA_DEVICE cookie; the database keeps only its SHA-256 hash, so a database leak does not
 * reveal usable tokens. Tokens are never logged or returned in a response body.
 */
@Service
public class DeviceService {

    public static final String DEVICE_COOKIE_NAME = "REVKASSA_DEVICE";

    // Browsers cap cookie lifetime at 400 days; every PIN login renews it (B04).
    private static final Duration DEVICE_COOKIE_MAX_AGE = Duration.ofDays(400);

    private final TrustedDeviceRepository trustedDeviceRepository;
    private final TokenService tokenService;
    private final boolean secureCookies;

    public DeviceService(TrustedDeviceRepository trustedDeviceRepository,
                         TokenService tokenService,
                         @Value("${revkassa.secure-cookies}") boolean secureCookies) {
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.tokenService = tokenService;
        this.secureCookies = secureCookies;
    }

    public Optional<TrustedDevice> findTrusted(String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return Optional.empty();
        }
        return trustedDeviceRepository.findActiveByTokenHash(hash(deviceToken));
    }

    public String createToken() {
        return tokenService.createToken();
    }

    public String hash(String deviceToken) {
        return tokenService.hash(deviceToken);
    }

    public ResponseCookie createDeviceCookie(String deviceToken) {
        return ResponseCookie.from(DEVICE_COOKIE_NAME, deviceToken)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/")
                .maxAge(DEVICE_COOKIE_MAX_AGE)
                .build();
    }
}
