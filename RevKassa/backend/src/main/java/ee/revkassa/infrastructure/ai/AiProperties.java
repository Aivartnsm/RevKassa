package ee.revkassa.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * The optional event assistant (Google Gemini). It is on only when apiKey is set: env GOOGLE_API_KEY, from a Cloud
 * project with billing (paid service: prompts are not used to improve Google's products).
 *
 * @param maxAttempts calls to the API in all, the first one included: 2 = one retry
 */
@ConfigurationProperties("revkassa.ai")
public record AiProperties(String apiKey,
                           @DefaultValue("gemini-3.5-flash-lite") String model,
                           @DefaultValue("4000") int maxOutputTokens,
                           @DefaultValue("60s") Duration timeout,
                           @DefaultValue("2") int maxAttempts) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // Never print the key, e.g. when Spring logs a failed binding
    @Override
    public String toString() {
        return "AiProperties[model=" + model + ", maxOutputTokens=" + maxOutputTokens + ", timeout=" + timeout
                + ", maxAttempts=" + maxAttempts + ", apiKey=" + (hasApiKey() ? "***" : "unset") + "]";
    }
}
