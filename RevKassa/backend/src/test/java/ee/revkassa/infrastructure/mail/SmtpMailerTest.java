package ee.revkassa.infrastructure.mail;

import ee.revkassa.infrastructure.config.RevkassaProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpMailerTest {

    private static final MailMessage MESSAGE =
            new MailMessage("liige@example.org", "Parooli määramine", "Ava link: https://kassa.example.org/set-password?token=secret");

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void send_buildsPlainTextMessageWithSenderAndReplyTo() {
        new SmtpMailer(mailSender, new RevkassaProperties.Mail("kassa@example.org", "juhatus@example.org")).send(MESSAGE);

        SimpleMailMessage sent = capturedMessage();
        assertThat(sent.getFrom()).isEqualTo("kassa@example.org");
        assertThat(sent.getReplyTo()).isEqualTo("juhatus@example.org");
        assertThat(sent.getTo()).containsExactly("liige@example.org");
        assertThat(sent.getSubject()).isEqualTo("Parooli määramine");
        assertThat(sent.getText()).isEqualTo(MESSAGE.body());
    }

    @Test
    void send_withoutReplyTo_leavesItOut() {
        new SmtpMailer(mailSender, new RevkassaProperties.Mail("kassa@example.org", " ")).send(MESSAGE);

        assertThat(capturedMessage().getReplyTo()).isNull();
    }

    @Test
    void send_serverFailure_throwsMailSendFailedWithoutBodyOrAddress() {
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));
        SmtpMailer mailer = new SmtpMailer(mailSender, new RevkassaProperties.Mail("kassa@example.org", null));

        assertThatThrownBy(() -> mailer.send(MESSAGE))
                .isInstanceOf(MailSendFailedException.class)
                .satisfies(e -> assertThat(((MailSendFailedException) e).getReason())
                        .contains("MailSendException")
                        .doesNotContain("token=secret")
                        .doesNotContain("liige@example.org"));
    }

    private SimpleMailMessage capturedMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
