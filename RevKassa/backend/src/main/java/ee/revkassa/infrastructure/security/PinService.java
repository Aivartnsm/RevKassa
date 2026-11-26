package ee.revkassa.infrastructure.security;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * A PIN alone identifies a member, so the server must find the member by PIN without storing the PIN.
 * The member row keeps HMAC-SHA256(server secret, PIN) instead. PINs are never logged or put in messages.
 */
@Service
public class PinService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int PIN_LENGTH = 6;
    private static final int PIN_UPPER_BOUND = 1_000_000;

    private final MemberRepository memberRepository;
    private final RandomGenerator randomGenerator;
    private final SecretKeySpec secretKey;

    public PinService(MemberRepository memberRepository,
                      RandomGenerator randomGenerator,
                      @Value("${revkassa.pin-secret}") String pinSecret) {
        this.memberRepository = memberRepository;
        this.randomGenerator = randomGenerator;
        this.secretKey = createSecretKey(pinSecret);
    }

    public Optional<Member> findByPin(String pin) {
        return memberRepository.findByPinHmac(hash(pin));
    }

    public boolean matches(String pin, String pinHmac) {
        return MessageDigest.isEqual(hash(pin).getBytes(StandardCharsets.UTF_8), pinHmac.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isTaken(String pin) {
        return memberRepository.existsByPinHmac(hash(pin));
    }

    public String generateUniquePin() {
        String pin;
        do {
            pin = randomPin();
        } while (isTaken(pin));
        return pin;
    }

    public String hash(String pin) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] digest = mac.doFinal(pin.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PIN hashing is not available", e);
        }
    }

    private String randomPin() {
        int number = randomGenerator.nextInt(PIN_UPPER_BOUND);
        return String.format("%0" + PIN_LENGTH + "d", number);
    }

    private static SecretKeySpec createSecretKey(String pinSecret) {
        if (pinSecret == null || pinSecret.isBlank()) {
            throw new IllegalStateException("revkassa.pin-secret (env REVKASSA_PIN_SECRET) must not be empty");
        }
        return new SecretKeySpec(pinSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }
}
