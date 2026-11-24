package ee.revkassa.infrastructure.mail;

/**
 * The only way the app sends email. SMTP when spring.mail.host is set, otherwise written to the log (development).
 * Tests use a fake. Sending never changes balances or other data; callers record the outcome themselves.
 */
public interface Mailer {

    /**
     * @throws MailSendFailedException when the mail server refuses or cannot be reached
     */
    void send(MailMessage message);
}
