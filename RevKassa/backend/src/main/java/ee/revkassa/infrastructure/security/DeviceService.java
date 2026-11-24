package ee.revkassa.infrastructure.security;

import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import ee.revkassa.persistence.trusteddevice.TrustedDeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.random.RandomGenerator;

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
    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final TrustedDeviceRepository trustedDeviceRepository;
    private final RandomGenerator randomGenerator;
    private final boolean secureCookies;

    public DeviceService(TrustedDeviceRepository trustedDeviceRepository,
                         RandomGenerator randomGenerator,
                         @Value("${revkassa.secure-cookies}") boolean secureCookies) {
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.randomGenerator = randomGenerator;
        this.secureCookies = secureCookies;
    }

    public Optional<TrustedDevice> findTrusted(String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return Optional.empty();
        }
        return trustedDeviceRepository.findActiveByTokenHash(hash(deviceToken));
    }

    public String createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        randomGenerator.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String deviceToken) {
        byte[] digest = sha256().digest(deviceToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
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

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Device token hashing is not available", e);
        }
    }
}
