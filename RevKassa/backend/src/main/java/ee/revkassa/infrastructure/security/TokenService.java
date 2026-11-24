package ee.revkassa.infrastructure.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

/**
 * Random secret tokens (device cookie, password links) and their SHA-256 hash. The database keeps only the hash,
 * so a database leak does not reveal usable tokens. 32 random bytes are too many to guess, so a fast hash is enough.
 */
@Service
public class TokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RandomGenerator randomGenerator;

    public TokenService(RandomGenerator randomGenerator) {
        this.randomGenerator = randomGenerator;
    }

    /**
     * 32 random bytes, URL-safe Base64 without padding (43 characters), safe to put in a link or cookie as is.
     */
    public String createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        randomGenerator.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        byte[] digest = sha256().digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Token hashing is not available", e);
        }
    }
}
