package ee.revkassa.infrastructure.mail;

import lombok.Getter;

/**
 * Sending one email failed. The reason is short and safe to store with the email (e.g. invoice status FAILED)
 * and to show to an admin; it never contains the message body.
 */
@Getter
public class MailSendFailedException extends RuntimeException {

    private final String reason;

    public MailSendFailedException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }
}
