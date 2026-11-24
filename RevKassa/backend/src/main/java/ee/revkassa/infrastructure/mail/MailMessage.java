package ee.revkassa.infrastructure.mail;

/**
 * One plain text email to one recipient. Sender and reply-to come from settings (revkassa.mail.*).
 */
public record MailMessage(String to, String subject, String body) {
}
