package ai.attenso.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * L'horloge est injectée partout plutôt que lue par {@code Instant.now()} :
 * la grâce, le seuil de prévenance et la purge se testent en avançant le temps,
 * pas en attendant.
 *
 * <p>La zone est Europe/Paris, pas UTC : le produit sert des cliniques
 * françaises, et « aujourd'hui » est le jour de la clinique. En UTC, entre
 * minuit et 1h (2h en été) à Paris, l'application croirait encore à la veille :
 * un ticket prendrait le numéro d'ordre de la veille, la fin d'abonnement
 * d'hier ne fermerait pas la clinique, la purge et la mesure journalières se
 * rangeraient sur le mauvais jour. Pas de propriété de configuration : une
 * zone paramétrable serait de la généralité sans client pour l'exiger.
 */
@Configuration
public class ConfigurationHorloge {

	static final ZoneId ZONE_CLINIQUE = ZoneId.of("Europe/Paris");

	@Bean
	Clock horloge() {
		return Clock.system(ZONE_CLINIQUE);
	}
}
