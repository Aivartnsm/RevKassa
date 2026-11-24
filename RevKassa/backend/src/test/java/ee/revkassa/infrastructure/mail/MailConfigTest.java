package ee.revkassa.infrastructure.mail;

import ee.revkassa.infrastructure.config.RevkassaProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which Mailer the app gets and how revkassa.* email settings bind, without starting the whole app.
 */
@ExtendWith(OutputCaptureExtension.class)
class MailConfigTest {

    private static final String[] COMPLETE_SETTINGS = {
            "spring.mail.host=smtp.example.org",
            "revkassa.app-url=https://kassa.example.org",
            "revkassa.mail.from=RevKassa <kassa@example.org>",
            "revkassa.invoice.org-name=Näidisklubi MTÜ",
            "revkassa.invoice.iban=EE382200221020145685"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(MailConfig.class));

    @Test
    void withoutHost_usesLoggingMailerAndNeedsNoSettings() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Mailer.class)).isInstanceOf(LoggingMailer.class);
        });
    }

    @Test
    void withHostAndAllSettings_usesSmtpMailer() {
        contextRunner.withPropertyValues(COMPLETE_SETTINGS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(Mailer.class)).isInstanceOf(SmtpMailer.class);
        });
    }

    @Test
    void withHostButNoSettings_failsAtStartupListingWhatIsMissing() {
        contextRunner.withPropertyValues("spring.mail.host=smtp.example.org").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("revkassa.app-url")
                    .hasMessageContaining("revkassa.mail.from")
                    .hasMessageContaining("revkassa.invoice.org-name")
                    .hasMessageContaining("revkassa.invoice.iban");
        });
    }

    @Test
    void withHostAndBlankSender_failsAtStartup() {
        contextRunner.withPropertyValues(COMPLETE_SETTINGS).withPropertyValues("revkassa.mail.from= ").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("revkassa.mail.from")
                    .message().doesNotContain("revkassa.app-url");
        });
    }

    @Test
    void withHostAndZeroDueDays_failsAtStartup() {
        contextRunner.withPropertyValues(COMPLETE_SETTINGS).withPropertyValues("revkassa.invoice.due-days=0").run(context ->
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("due-days"));
    }

    @Test
    void withHostAndLocalhostAppUrl_startsButWarns(CapturedOutput output) {
        contextRunner.withPropertyValues(COMPLETE_SETTINGS).withPropertyValues("revkassa.app-url=http://localhost:5173")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(output).contains("set REVKASSA_APP_URL to the public address");
                });
    }

    @Test
    void withHostAndPublicAppUrl_doesNotWarn(CapturedOutput output) {
        contextRunner.withPropertyValues(COMPLETE_SETTINGS).run(context ->
                assertThat(output).doesNotContain("set REVKASSA_APP_URL"));
    }

    @Test
    void settingsBind_withDefaultDueDaysOf14() {
        contextRunner.withPropertyValues(COMPLETE_SETTINGS)
                .withPropertyValues("revkassa.mail.reply-to=juhatus@example.org")
                .run(context -> {
                    RevkassaProperties properties = context.getBean(RevkassaProperties.class);
                    assertThat(properties.appUrl()).isEqualTo("https://kassa.example.org");
                    assertThat(properties.mail().from()).isEqualTo("RevKassa <kassa@example.org>");
                    assertThat(properties.mail().replyTo()).isEqualTo("juhatus@example.org");
                    assertThat(properties.invoice().orgName()).isEqualTo("Näidisklubi MTÜ");
                    assertThat(properties.invoice().iban()).isEqualTo("EE382200221020145685");
                    assertThat(properties.invoice().dueDays()).isEqualTo(14);
                });
    }

    @Test
    void dueDays_canBeChanged() {
        contextRunner.withPropertyValues("revkassa.invoice.due-days=30").run(context ->
                assertThat(context.getBean(RevkassaProperties.class).invoice().dueDays()).isEqualTo(30));
    }
}
