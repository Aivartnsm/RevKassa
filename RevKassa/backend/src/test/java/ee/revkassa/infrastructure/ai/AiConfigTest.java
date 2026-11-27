package ee.revkassa.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which EventAssistant the app gets and how revkassa.ai.* binds, without starting the whole app. Building the Gemini
 * client does not call Google.
 */
@ExtendWith(OutputCaptureExtension.class)
class AiConfigTest {

    private static final String KEY = "test-key-never-printed";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AiConfig.class));

    @Test
    void withoutKey_usesDisabledAssistant() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            EventAssistant assistant = context.getBean(EventAssistant.class);
            assertThat(assistant).isInstanceOf(DisabledEventAssistant.class);
            assertThat(assistant.isEnabled()).isFalse();
        });
    }

    @Test
    void withBlankKey_usesDisabledAssistant() {
        contextRunner.withPropertyValues("revkassa.ai.api-key=   ").run(context ->
                assertThat(context.getBean(EventAssistant.class)).isInstanceOf(DisabledEventAssistant.class));
    }

    @Test
    void withKey_usesGeminiAssistant_andNeverLogsTheKey(CapturedOutput output) {
        contextRunner.withPropertyValues("revkassa.ai.api-key=" + KEY).run(context -> {
            assertThat(context).hasNotFailed();
            EventAssistant assistant = context.getBean(EventAssistant.class);
            assertThat(assistant).isInstanceOf(GeminiEventAssistant.class);
            assertThat(assistant.isEnabled()).isTrue();
            assertThat(context.getBean(AiProperties.class).toString()).doesNotContain(KEY);
        });
        assertThat(output).contains("Event assistant is on").doesNotContain(KEY);
    }

    @Test
    void defaults_matchThePlan() {
        contextRunner.run(context -> {
            AiProperties properties = context.getBean(AiProperties.class);
            assertThat(properties.model()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(properties.maxOutputTokens()).isEqualTo(4000);
            assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.maxAttempts()).isEqualTo(2);
        });
    }
}
