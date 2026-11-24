package ee.revkassa.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
public class RandomConfig {

    // PINs and tokens are credentials, so they need a cryptographically strong generator.
    // A bean (instead of new SecureRandom() in place) lets tests replace it with predictable values.
    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
