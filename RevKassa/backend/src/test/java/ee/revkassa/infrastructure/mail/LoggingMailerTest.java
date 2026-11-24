package ee.revkassa.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LoggingMailerTest {

    @Test
    void send_writesWholeEmailToLogSoDevelopersCanOpenLinks(CapturedOutput output) {
        new LoggingMailer().send(new MailMessage("liige@example.org", "Parooli määramine", "Ava link: http://localhost:5173/x"));

        assertThat(output)
                .contains("Email NOT sent")
                .contains("To: liige@example.org")
                .contains("Subject: Parooli määramine")
                .contains("Ava link: http://localhost:5173/x");
    }
}
