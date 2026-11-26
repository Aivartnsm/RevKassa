package ee.revkassa.infrastructure.security;

import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.TooManyAttemptsException;
import ee.revkassa.testdata.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test: lockout rules with a clock the test moves by hand.
 */
class LoginLockoutServiceTest {

    private static final LockoutKey TABLET = LockoutKey.forDevice(1L);
    private static final LockoutKey OTHER_TABLET = LockoutKey.forDevice(2L);
    private static final LockoutKey IP = LockoutKey.forIp("10.0.0.1");
    private static final LockoutKey OTHER_IP = LockoutKey.forIp("10.0.0.2");

    private TestClock clock;
    private LoginLockoutService lockoutService;

    @BeforeEach
    void createService() {
        clock = new TestClock(Instant.parse("2026-11-24T18:00:00Z"));
        lockoutService = new LoginLockoutService(clock);
    }

    @Test
    void firstFourFailures_return403WithRemainingAttemptsCountingDown() {
        for (int expectedRemaining = 4; expectedRemaining >= 1; expectedRemaining--) {
            RuntimeException failure = lockoutService.registerFailure(TABLET);

            assertThat(failure).isInstanceOf(ForbiddenException.class);
            assertThat(((ForbiddenException) failure).getError()).isEqualTo(Error.WRONG_PIN);
            assertThat(((ForbiddenException) failure).getRemainingAttempts()).isEqualTo(expectedRemaining);
        }
    }

    @Test
    void fifthFailure_locksForOneMinuteAndAnnouncesFiveMinutes() {
        failTimes(TABLET, 4);

        TooManyAttemptsException locked = (TooManyAttemptsException) lockoutService.registerFailure(TABLET);

        assertThat(locked.getError()).isEqualTo(Error.TOO_MANY_WRONG_PINS);
        assertThat(locked.getRetryAfterSeconds()).isEqualTo(60);
        assertThat(locked.getNextLockoutSeconds()).isEqualTo(300);
    }

    @Test
    void afterLockoutEnds_everyFailureLocksAgainWithGrowingLevelsUpToOneDay() {
        failTimes(TABLET, 5);
        long[][] expectedLockoutAndNext = {
                {300, 600}, {600, 1800}, {1800, 3600}, {3600, 86400}, {86400, 86400}, {86400, 86400}};
        long previousLockout = 60;

        for (long[] expected : expectedLockoutAndNext) {
            clock.advance(Duration.ofSeconds(previousLockout));
            TooManyAttemptsException locked = (TooManyAttemptsException) lockoutService.registerFailure(TABLET);

            assertThat(locked.getRetryAfterSeconds()).isEqualTo(expected[0]);
            assertThat(locked.getNextLockoutSeconds()).isEqualTo(expected[1]);
            previousLockout = expected[0];
        }
    }

    @Test
    void whileLocked_ensureNotLockedThrowsWithRemainingTime() {
        failTimes(TABLET, 5);
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> lockoutService.ensureNotLocked(TABLET))
                .isInstanceOfSatisfying(TooManyAttemptsException.class, locked -> {
                    assertThat(locked.getRetryAfterSeconds()).isEqualTo(40);
                    assertThat(locked.getNextLockoutSeconds()).isEqualTo(300);
                });
    }

    @Test
    void whileLocked_remainingTimeIsRoundedUpToWholeSeconds() {
        failTimes(TABLET, 5);
        clock.advance(Duration.ofMillis(59_500));

        assertThatThrownBy(() -> lockoutService.ensureNotLocked(TABLET))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        locked -> assertThat(locked.getRetryAfterSeconds()).isEqualTo(1));
    }

    @Test
    void failureWhileLocked_doesNotExtendLockout() {
        failTimes(TABLET, 5);
        clock.advance(Duration.ofSeconds(30));

        TooManyAttemptsException locked = (TooManyAttemptsException) lockoutService.registerFailure(TABLET);

        assertThat(locked.getRetryAfterSeconds()).isEqualTo(30);
        assertThat(locked.getNextLockoutSeconds()).isEqualTo(300);
    }

    @Test
    void lockoutEnds_afterItsDuration() {
        failTimes(TABLET, 5);
        clock.advance(Duration.ofSeconds(60));

        assertThatCode(() -> lockoutService.ensureNotLocked(TABLET)).doesNotThrowAnyException();
    }

    @Test
    void success_resetsCounterAndLevel() {
        failTimes(TABLET, 5);
        clock.advance(Duration.ofSeconds(60));
        lockoutService.registerSuccess(TABLET);

        RuntimeException failure = lockoutService.registerFailure(TABLET);

        assertThat(((ForbiddenException) failure).getRemainingAttempts()).isEqualTo(4);
    }

    @Test
    void lockout_isPerDevice() {
        failTimes(TABLET, 5);

        assertThatCode(() -> lockoutService.ensureNotLocked(OTHER_TABLET)).doesNotThrowAnyException();
        assertThat(((ForbiddenException) lockoutService.registerFailure(OTHER_TABLET)).getRemainingAttempts()).isEqualTo(4);
    }

    @Test
    void passwordLockout_isPerIpAndUsesItsOwnErrors() {
        failTimes(IP, 4);

        TooManyAttemptsException locked = (TooManyAttemptsException) lockoutService.registerFailure(IP);

        assertThat(locked.getError()).isEqualTo(Error.TOO_MANY_FAILED_LOGINS);
        assertThatCode(() -> lockoutService.ensureNotLocked(OTHER_IP)).doesNotThrowAnyException();
        assertThat(((ForbiddenException) lockoutService.registerFailure(OTHER_IP)).getError())
                .isEqualTo(Error.WRONG_EMAIL_OR_PASSWORD);
    }

    @Test
    void pinAndPasswordCounters_areSeparateEvenWithSameId() {
        failTimes(LockoutKey.forDevice(7L), 5);

        assertThatCode(() -> lockoutService.ensureNotLocked(LockoutKey.forIp("7"))).doesNotThrowAnyException();
    }

    @Test
    void passwordChange_hasOwnCounterAndErrorCodes() {
        RuntimeException wrong = lockoutService.registerFailure(LockoutKey.forPasswordChange(7L));
        failTimes(LockoutKey.forPasswordChange(7L), 3);
        RuntimeException locked = lockoutService.registerFailure(LockoutKey.forPasswordChange(7L));

        assertThat(((ForbiddenException) wrong).getError()).isEqualTo(Error.WRONG_CURRENT_PASSWORD);
        assertThat(((TooManyAttemptsException) locked).getError()).isEqualTo(Error.TOO_MANY_FAILED_LOGINS);
        assertThatCode(() -> lockoutService.ensureNotLocked(LockoutKey.forIp("7"))).doesNotThrowAnyException();
        assertThatCode(() -> lockoutService.ensureNotLocked(LockoutKey.forDevice(7L))).doesNotThrowAnyException();
    }

    @Test
    void pinChange_hasOwnCounterAndCanAnswerWithAnotherErrorOnTheSameCounter() {
        LockoutKey key = LockoutKey.forPinChange(7L);

        RuntimeException wrong = lockoutService.registerFailure(key);
        RuntimeException notAvailable = lockoutService.registerFailure(key, Error.PIN_NOT_AVAILABLE);
        failTimes(key, 2);
        RuntimeException locked = lockoutService.registerFailure(key);

        assertThat(((ForbiddenException) wrong).getError()).isEqualTo(Error.WRONG_CURRENT_PIN);
        assertThat(((ForbiddenException) notAvailable).getError()).isEqualTo(Error.PIN_NOT_AVAILABLE);
        assertThat(((ForbiddenException) notAvailable).getRemainingAttempts()).isEqualTo(3);
        assertThat(((TooManyAttemptsException) locked).getError()).isEqualTo(Error.TOO_MANY_WRONG_PINS);
        assertThatCode(() -> lockoutService.ensureNotLocked(LockoutKey.forDevice(7L))).doesNotThrowAnyException();
        assertThatCode(() -> lockoutService.ensureNotLocked(LockoutKey.forPasswordChange(7L))).doesNotThrowAnyException();
    }

    private void failTimes(LockoutKey key, int times) {
        for (int i = 0; i < times; i++) {
            lockoutService.registerFailure(key);
        }
    }
}
