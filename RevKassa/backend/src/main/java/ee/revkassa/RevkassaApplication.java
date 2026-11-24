package ee.revkassa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// No default in-memory user: members log in through AuthController (PIN or email + password)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class RevkassaApplication {

	public static void main(String[] args) {
		SpringApplication.run(RevkassaApplication.class, args);
	}

}
