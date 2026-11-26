package ee.revkassa.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// One instance runs the app (PLAN.md), so plain @Scheduled is enough: no shared scheduler lock
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
