package ee.revkassa.infrastructure.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The event assistant is on only when revkassa.ai.api-key (env GOOGLE_API_KEY) is set; otherwise the app runs
 * without it and the UI hides it.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public EventAssistant eventAssistant(AiProperties properties) {
        if (!properties.hasApiKey()) {
            log.info("Event assistant is off: GOOGLE_API_KEY is not set");
            return new DisabledEventAssistant();
        }
        log.info("Event assistant is on: Gemini model {}", properties.model());
        return new GeminiEventAssistant(properties);
    }
}
