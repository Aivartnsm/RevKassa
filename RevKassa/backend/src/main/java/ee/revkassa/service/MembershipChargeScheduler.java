package ee.revkassa.service;

import ee.revkassa.infrastructure.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

/**
 * Charges the current month's fees once at startup and every day at 06:00 club time: the 1st charges the month, later
 * runs make up for a server that was down on the 1st. Off in tests (revkassa.membership-fees.job-enabled=false):
 * tests share the dev database and must not commit charges to it.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "revkassa.membership-fees.job-enabled", havingValue = "true", matchIfMissing = true)
public class MembershipChargeScheduler {

    private final MembershipChargeService membershipChargeService;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 6 * * *", zone = ClockConfig.APP_ZONE_ID)
    public void chargeCurrentMonth() {
        membershipChargeService.chargeMonth(YearMonth.now(clock));
    }
}
