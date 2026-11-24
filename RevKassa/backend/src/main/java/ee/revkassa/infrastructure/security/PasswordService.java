package ee.revkassa.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Checks bcrypt password hashes. Raw passwords and their hashes are never logged or returned.
 */
@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Without a stored hash (unknown email or password not set yet) a dummy hash is still checked, so the response
     * time does not reveal which emails exist.
     */
    public boolean matches(String rawPassword, String passwordHash) {
        boolean matches = passwordEncoder.matches(rawPassword, passwordHash != null ? passwordHash : dummyHash);
        return passwordHash != null && matches;
    }
}
