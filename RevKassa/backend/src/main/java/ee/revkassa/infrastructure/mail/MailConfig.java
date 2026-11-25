package ee.revkassa.infrastructure.mail;

import ee.revkassa.infrastructure.config.RevkassaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Boot creates a JavaMailSender only when spring.mail.host is set. With it we send over SMTP and every
 * setting real emails need must be present, otherwise the app does not start. Without it emails go to the log.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RevkassaProperties.class)
public class MailConfig {

    @Bean
    public Mailer mailer(ObjectProvider<JavaMailSender> mailSender, RevkassaProperties properties) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        if (smtp == null) {
            log.warn("spring.mail.host is not set: emails are written to the log, not sent (development only)");
            return new LoggingMailer();
        }
        validateForSmtp(properties);
        log.info("Emails are sent over SMTP from {}, links point to {}", properties.mail().from(), properties.appUrl());
        if (properties.appUrl().contains("localhost")) {
            // Fine with a local test SMTP server, wrong in production: members would get links they cannot open
            log.warn("SMTP is on but revkassa.app-url is {}: set REVKASSA_APP_URL to the public address", properties.appUrl());
        }
        return new SmtpMailer(smtp, properties.mail());
    }

    private static void validateForSmtp(RevkassaProperties properties) {
        List<String> missing = new ArrayList<>();
        requireText(properties.appUrl(), "revkassa.app-url", missing);
        requireText(properties.mail().from(), "revkassa.mail.from", missing);
        if (properties.invoice().dueDays() < 1) {
            missing.add("revkassa.invoice.due-days (must be at least 1)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("SMTP is on (spring.mail.host), but these settings are missing: " + missing);
        }
    }

    private static void requireText(String value, String name, List<String> missing) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }
}
