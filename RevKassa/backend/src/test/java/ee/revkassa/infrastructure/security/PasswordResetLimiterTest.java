package ee.revkassa.infrastructure.security;

import ee.revkassa.infrastructure.exception.TooManyAttemptsException;
import ee.revkassa.testdata.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetLimiterTest {

    private final TestClock clock = new TestClock(Instant.parse("2026-11-24T10:00:00Z"));
    private final PasswordResetLimiter limiter = new PasswordResetLimiter(clock);

    @Test
    void ip_sixthRequestWithinHour_isRefusedWithSecondsUntilOldestLeaves() {
        fiveRequestsWithOldestLeavingIn(Duration.ofMinutes(10));

        assertThatThrownBy(() -> limiter.acquireForIp("1.2.3.4"))
                .isInstanceOfSatisfying(TooManyAttemptsException.class, e -> {
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(Duration.ofMinutes(10).toSeconds());
                    assertThat(e.getNextLockoutSeconds()).isZero();
                });
    }

    @Test
    void ip_oldestRequestLeavesWindow_allowsOneMore() {
        fiveRequestsWithOldestLeavingIn(Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(10));

        assertThatNoException().isThrownBy(() -> limiter.acquireForIp("1.2.3.4"));
        assertThatThrownBy(() -> limiter.acquireForIp("1.2.3.4")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void ip_refusedRequestsDoNotExtendTheWait() {
        for (int i = 0; i < PasswordResetLimiter.MAX_REQUESTS; i++) {
            limiter.acquireForIp("1.2.3.4");
        }
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limiter.acquireForIp("1.2.3.4")).isInstanceOf(TooManyAttemptsException.class);
        }
        clock.advance(PasswordResetLimiter.WINDOW);

        assertThatNoException().isThrownBy(() -> limiter.acquireForIp("1.2.3.4"));
    }

    @Test
    void ip_otherIpIsNotAffected() {
        for (int i = 0; i < PasswordResetLimiter.MAX_REQUESTS; i++) {
            limiter.acquireForIp("1.2.3.4");
        }

        assertThatNoException().isThrownBy(() -> limiter.acquireForIp("5.6.7.8"));
    }

    @Test
    void email_limitIgnoresCaseAndFreesAfterOneHour() {
        for (int i = 0; i < PasswordResetLimiter.MAX_REQUESTS; i++) {
            assertThat(limiter.tryAcquireForEmail("Liige@Example.org")).isTrue();
        }

        assertThat(limiter.tryAcquireForEmail("liige@example.org")).isFalse();
        clock.advance(PasswordResetLimiter.WINDOW);
        assertThat(limiter.tryAcquireForEmail("liige@example.org")).isTrue();
    }

    // One request, then four more later, so that the first leaves the one-hour window `leavesIn` from now
    private void fiveRequestsWithOldestLeavingIn(Duration leavesIn) {
        limiter.acquireForIp("1.2.3.4");
        clock.advance(PasswordResetLimiter.WINDOW.minus(leavesIn));
        for (int i = 1; i < PasswordResetLimiter.MAX_REQUESTS; i++) {
            limiter.acquireForIp("1.2.3.4");
        }
    }
}
