package ai.attenso.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * L'horloge est injectée partout plutôt que lue par {@code Instant.now()} :
 * la grâce, le seuil de prévenance et la purge se testent en avançant le temps,
 * pas en attendant.
 */
@Configuration
public class ConfigurationHorloge {

	@Bean
	Clock horloge() {
		return Clock.systemUTC();
	}
}
