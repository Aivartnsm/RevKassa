package ee.revkassa.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    // The club's time zone: the clock and every time in API responses use it
    public static final String APP_ZONE_ID = "Europe/Tallinn";
    public static final ZoneId APP_ZONE = ZoneId.of(APP_ZONE_ID);

    // Ticks in microseconds like PostgreSQL timestamptz, so a time returned right after saving is the same as the one
    // read back from the database later
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.system(APP_ZONE), Duration.ofNanos(1_000));
    }
}
