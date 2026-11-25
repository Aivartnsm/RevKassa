package ee.revkassa.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * App settings that emails need (B25): link base, sender and invoice due days. Env vars: REVKASSA_APP_URL,
 * REVKASSA_MAIL_FROM, REVKASSA_MAIL_REPLY_TO, REVKASSA_INVOICE_DUE_DAYS. The payee name and IBAN are in the database
 * (organisation, B39), set by an admin.
 * Required only when SMTP is on, see MailConfig.
 */
@ConfigurationProperties("revkassa")
public record RevkassaProperties(String appUrl, @DefaultValue Mail mail, @DefaultValue Invoice invoice) {

    public record Mail(String from, String replyTo) {
    }

    public record Invoice(@DefaultValue("14") int dueDays) {
    }
}
