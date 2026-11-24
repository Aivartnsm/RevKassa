package ee.revkassa.infrastructure.security;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import ee.revkassa.testdata.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(TestData.class)
class DeviceServiceTest {

    // docs/database/3_import.sql: "Arenduse tahvel" is stored as SHA-256("dev-tablet-token")
    private static final String DEV_TABLET_TOKEN_HASH = "c63597b9cc342bb90ede96dcdd159f7ca789748d66c2b31c7d0a14f40f8764b7";

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private TestData testData;

    private Member admin;

    @BeforeEach
    void createAdmin() {
        admin = testData.member("device-test-admin@example.org", "device-test-pin-hmac");
    }

    @Test
    void hash_isSha256HexMatchingImportScript() {
        String hash = deviceService.hash("dev-tablet-token");

        assertThat(hash).isEqualTo(DEV_TABLET_TOKEN_HASH);
    }

    @Test
    void createToken_is32BytesBase64UrlWithoutPadding() {
        String token = deviceService.createToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void createToken_givesDifferentTokenEachTime() {
        assertThat(deviceService.createToken()).isNotEqualTo(deviceService.createToken());
    }

    @Test
    void findTrusted_activeDevice_returnsDevice() {
        TrustedDevice saved = testData.trustedDevice("Test tahvel", deviceService.hash("test-token-1"), admin);

        Optional<TrustedDevice> found = deviceService.findTrusted("test-token-1");

        assertThat(found).get().extracting(TrustedDevice::getId).isEqualTo(saved.getId());
    }

    @Test
    void findTrusted_revokedDevice_returnsEmpty() {
        TrustedDevice device = testData.trustedDevice("Test tahvel", deviceService.hash("test-token-2"), admin);
        testData.revoke(device, admin);

        Optional<TrustedDevice> found = deviceService.findTrusted("test-token-2");

        assertThat(found).isEmpty();
    }

    @Test
    void findTrusted_unknownToken_returnsEmpty() {
        Optional<TrustedDevice> found = deviceService.findTrusted("no-such-token");

        assertThat(found).isEmpty();
    }

    @Test
    void findTrusted_missingOrBlankToken_returnsEmpty() {
        assertThat(deviceService.findTrusted(null)).isEmpty();
        assertThat(deviceService.findTrusted("  ")).isEmpty();
    }

    @Test
    void findTrusted_storedHashUsedAsToken_returnsEmpty() {
        testData.trustedDevice("Test tahvel", deviceService.hash("test-token-3"), admin);

        Optional<TrustedDevice> found = deviceService.findTrusted(deviceService.hash("test-token-3"));

        assertThat(found).isEmpty();
    }

    @Test
    void createDeviceCookie_isHttpOnlyStrictAndLongLived() {
        ResponseCookie cookie = deviceService.createDeviceCookie("some-token");

        assertThat(cookie.getName()).isEqualTo("REVKASSA_DEVICE");
        assertThat(cookie.getValue()).isEqualTo("some-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(400));
        assertThat(cookie.isSecure()).isFalse(); // dev/test over http; production profile sets revkassa.secure-cookies=true
    }
}
