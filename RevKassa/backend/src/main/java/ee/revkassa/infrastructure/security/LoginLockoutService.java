package ee.revkassa.infrastructure.security;

import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Brute-force protection for PIN (per trusted device) and password (per client IP) login.
 * 5 wrong attempts lock for 1 minute. After that every wrong attempt locks again, each time longer:
 * 5 min, 10 min, 30 min, 1 h, then 1 day. A successful login clears everything for that key.
 * While locked the PIN or password is not checked at all. Kept in memory: the app runs as a single instance,
 * so a restart clears all lockouts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockoutService {

    static final int ATTEMPTS_BEFORE_FIRST_LOCKOUT = 5;
    static final List<Duration> LOCKOUT_LEVELS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofDays(1));

    // Limits memory if many IPs fail once; only keys that are idle and not locked are dropped
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final Duration IDLE_KEY_LIFETIME = Duration.ofDays(1);

    private final Clock clock;
    private final Map<LockoutKey, Attempts> attemptsByKey = new HashMap<>();

    public synchronized void ensureNotLocked(LockoutKey key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts != null && attempts.isLockedAt(now())) {
            throw lockedException(key, attempts);
        }
    }

    /**
     * Counts one wrong attempt and returns the exception to throw: 403 with remainingAttempts before the first
     * lockout, otherwise 429.
     */
    public synchronized RuntimeException registerFailure(LockoutKey key) {
        Instant now = now();
        removeIdleKeysIfTooMany(now);
        Attempts attempts = attemptsByKey.computeIfAbsent(key, k -> new Attempts());
        attempts.lastFailureAt = now;
        if (attempts.isLockedAt(now)) {
            // a parallel request locked it meanwhile: do not extend
            return lockedException(key, attempts);
        }
        if (attempts.lockoutLevel == 0 && ++attempts.failures < ATTEMPTS_BEFORE_FIRST_LOCKOUT) {
            return new ForbiddenException(wrongCredentialsError(key), ATTEMPTS_BEFORE_FIRST_LOCKOUT - attempts.failures);
        }
        lock(key, attempts, now);
        return lockedException(key, attempts);
    }

    public synchronized void registerSuccess(LockoutKey key) {
        attemptsByKey.remove(key);
    }

    private Instant now() {
        return clock.instant();
    }

    private static void lock(LockoutKey key, Attempts attempts, Instant now) {
        Duration lockout = lockoutDuration(attempts.lockoutLevel);
        attempts.lockedUntil = now.plus(lockout);
        attempts.lockoutLevel++;
        log.warn("Login locked: method={}, key={}, seconds={}", key.loginMethod(), key.id(), lockout.toSeconds());
    }

    private TooManyAttemptsException lockedException(LockoutKey key, Attempts attempts) {
        long retryAfterSeconds = secondsRoundedUp(Duration.between(now(), attempts.lockedUntil));
        long nextLockoutSeconds = lockoutDuration(attempts.lockoutLevel).toSeconds();
        return new TooManyAttemptsException(lockedError(key), retryAfterSeconds, nextLockoutSeconds);
    }

    private static Duration lockoutDuration(int level) {
        return LOCKOUT_LEVELS.get(Math.min(level, LOCKOUT_LEVELS.size() - 1));
    }

    private static long secondsRoundedUp(Duration duration) {
        return (duration.toMillis() + 999) / 1000;
    }

    private static Error wrongCredentialsError(LockoutKey key) {
        return key.loginMethod() == LoginMethodEnum.PIN ? Error.WRONG_PIN : Error.WRONG_EMAIL_OR_PASSWORD;
    }

    private static Error lockedError(LockoutKey key) {
        return key.loginMethod() == LoginMethodEnum.PIN ? Error.TOO_MANY_WRONG_PINS : Error.TOO_MANY_FAILED_LOGINS;
    }

    private void removeIdleKeysIfTooMany(Instant now) {
        if (attemptsByKey.size() < MAX_TRACKED_KEYS) {
            return;
        }
        attemptsByKey.values().removeIf(attempts ->
                !attempts.isLockedAt(now) && attempts.lastFailureAt.plus(IDLE_KEY_LIFETIME).isBefore(now));
    }

    private static class Attempts {
        private int failures;
        private int lockoutLevel;
        private Instant lockedUntil;
        private Instant lastFailureAt;

        private boolean isLockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }
    }
}
