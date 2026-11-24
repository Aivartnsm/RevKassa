package ee.revkassa.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * App settings that emails need (B25): link base, sender and invoice payment details. They come from configuration,
 * not from the database. Env vars: REVKASSA_APP_URL, REVKASSA_MAIL_FROM, REVKASSA_MAIL_REPLY_TO,
 * REVKASSA_INVOICE_ORG_NAME, REVKASSA_INVOICE_IBAN, REVKASSA_INVOICE_DUE_DAYS.
 * Required only when SMTP is on, see MailConfig.
 */
@ConfigurationProperties("revkassa")
public record RevkassaProperties(String appUrl, @DefaultValue Mail mail, @DefaultValue Invoice invoice) {

    public record Mail(String from, String replyTo) {
    }

    public record Invoice(String orgName, String iban, @DefaultValue("14") int dueDays) {
    }
}
