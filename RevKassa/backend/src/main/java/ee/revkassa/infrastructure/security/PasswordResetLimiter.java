package ee.revkassa.infrastructure.security;

import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Forgot password?" sends email, so it is limited to 5 requests per hour per client IP (429 beyond that) and
 * 5 emails per hour per address (further requests are answered the same way, but no email goes out: the response
 * must not reveal whether the address exists). Kept in memory, like login lockouts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetLimiter {

    static final int MAX_REQUESTS = 5;
    static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> requestsByKey = new HashMap<>();

    public synchronized void acquireForIp(String ip) {
        Instant now = clock.instant();
        Deque<Instant> requests = recentRequests("ip:" + ip, now);
        if (requests.size() >= MAX_REQUESTS) {
            log.warn("Password reset limit reached: ip={}", ip);
            throw new TooManyAttemptsException(Error.TOO_MANY_PASSWORD_RESETS, secondsUntilFree(requests.getFirst(), now));
        }
        requests.addLast(now);
    }

    /**
     * @return false when this address already got the maximum number of emails within the window
     */
    public synchronized boolean tryAcquireForEmail(String email) {
        Instant now = clock.instant();
        Deque<Instant> requests = recentRequests("email:" + email.toLowerCase(Locale.ROOT), now);
        if (requests.size() >= MAX_REQUESTS) {
            return false;
        }
        requests.addLast(now);
        return true;
    }

    private Deque<Instant> recentRequests(String key, Instant now) {
        removeIdleKeysIfTooMany(now);
        Deque<Instant> requests = requestsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        removeOutsideWindow(requests, now);
        return requests;
    }

    private void removeIdleKeysIfTooMany(Instant now) {
        if (requestsByKey.size() < MAX_TRACKED_KEYS) {
            return;
        }
        requestsByKey.values().forEach(requests -> removeOutsideWindow(requests, now));
        requestsByKey.values().removeIf(Deque::isEmpty);
    }

    private static void removeOutsideWindow(Deque<Instant> requests, Instant now) {
        Instant windowStart = now.minus(WINDOW);
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(windowStart)) {
            requests.removeFirst();
        }
    }

    // The oldest request in the window leaves it first; round up so Retry-After is never too early
    private static long secondsUntilFree(Instant oldestRequest, Instant now) {
        Duration wait = Duration.between(now, oldestRequest.plus(WINDOW));
        return Math.max(1, wait.toSeconds() + (wait.toNanosPart() > 0 ? 1 : 0));
    }
}
