package ee.revkassa.infrastructure.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Development sender: without spring.mail.host emails are written to the log instead, so password links can be
 * copied from there. Never use it in production: the log would then contain one-time links.
 */
@Slf4j
public class LoggingMailer implements Mailer {

    @Override
    public void send(MailMessage message) {
        log.info("""
                Email NOT sent (no spring.mail.host), written to log instead:
                To: {}
                Subject: {}

                {}""", message.to(), message.subject(), message.body());
    }
}
