package ee.revkassa.testdata;

import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.infrastructure.mail.MailSendFailedException;
import ee.revkassa.infrastructure.mail.Mailer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps sent emails in memory instead of sending them. Use with @Import(FakeMailer.Config.class) and call clear()
 * before each test: the Spring context (and this bean) is shared between tests.
 */
public class FakeMailer implements Mailer {

    private final List<MailMessage> sent = new ArrayList<>();
    private boolean failNext;

    @Override
    public void send(MailMessage message) {
        if (failNext) {
            failNext = false;
            throw new MailSendFailedException("Email could not be sent: test failure", null);
        }
        sent.add(message);
    }

    public List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    public MailMessage last() {
        return sent.getLast();
    }

    public void failNextSend() {
        failNext = true;
    }

    public void clear() {
        sent.clear();
        failNext = false;
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public FakeMailer fakeMailer() {
            return new FakeMailer();
        }
    }
}
