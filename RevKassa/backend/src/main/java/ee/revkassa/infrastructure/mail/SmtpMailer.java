package ee.revkassa.infrastructure.mail;

import ee.revkassa.infrastructure.config.RevkassaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sends plain text email through the SMTP server from spring.mail.* (password from env SPRING_MAIL_PASSWORD).
 * Logs neither the recipient address nor the body: bodies can hold one-time password links.
 */
@Slf4j
public class SmtpMailer implements Mailer {

    private final JavaMailSender mailSender;
    private final RevkassaProperties.Mail settings;

    public SmtpMailer(JavaMailSender mailSender, RevkassaProperties.Mail settings) {
        this.mailSender = mailSender;
        this.settings = settings;
    }

    @Override
    public void send(MailMessage message) {
        try {
            mailSender.send(toSimpleMailMessage(message));
        } catch (MailException e) {
            log.warn("Email sending failed: subject='{}', reason={}", message.subject(), e.getClass().getSimpleName());
            throw new MailSendFailedException("Email could not be sent: " + e.getClass().getSimpleName(), e);
        }
    }

    private SimpleMailMessage toSimpleMailMessage(MailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(settings.from());
        if (hasText(settings.replyTo())) {
            mail.setReplyTo(settings.replyTo());
        }
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        return mail;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
