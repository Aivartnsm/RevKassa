package ee.revkassa.service;

import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Each admin may send the event assistant 20 questions per Tallinn day. Kept in memory (one instance; a restart
 * starts the count again). A question is counted when it is sent to the API, also when the API then fails: a failed
 * or timed-out call can still cost money.
 */
@Component
@RequiredArgsConstructor
class AssistantDailyLimit {

    static final int QUESTIONS_PER_DAY = 20;

    // One entry per admin; a new day replaces it
    private final Map<Long, Usage> usage = new HashMap<>();
    private final Clock clock;

    /**
     * Takes one question from the admin's allowance for today.
     *
     * @return the questions left today after this one
     * @throws TooManyAttemptsException none left; Retry-After is the next Tallinn midnight
     */
    synchronized int take(Long adminId) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ClockConfig.APP_ZONE);
        LocalDate today = now.toLocalDate();
        Usage current = usage.get(adminId);
        int used = current == null || !current.day().equals(today) ? 0 : current.count();
        if (used >= QUESTIONS_PER_DAY) {
            throw new TooManyAttemptsException(Error.ASSISTANT_DAILY_LIMIT, secondsUntilMidnight(now));
        }
        usage.put(adminId, new Usage(today, used + 1));
        return QUESTIONS_PER_DAY - used - 1;
    }

    private static long secondsUntilMidnight(ZonedDateTime now) {
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(ClockConfig.APP_ZONE);
        return Math.max(1, Duration.between(now, midnight).toSeconds());
    }

    private record Usage(LocalDate day, int count) {
    }
}
