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
 * françaises. Elle n'est lue que par ce qui date « aujourd'hui » depuis
 * l'horloge elle-même : la fin d'abonnement ({@code TacheFinAbonnement}), la
 * résiliation ({@code ServicePaiement}), la date d'inscription
 * ({@code ServiceInscription}), le mois facturé ({@code ServiceFacturation}) et
 * le semis de démonstration. En UTC, entre minuit et 1h (2h en été) à Paris,
 * ceux-là croiraient encore à la veille : un abonnement échu hier ne fermerait
 * pas la clinique. Le numéro d'ordre du jour, la purge et la mesure journalière
 * ne la lisent PAS : ils calculent le jour dans {@code tenant.zone()} et
 * étaient déjà justes en UTC. Pas de propriété de configuration : une zone
 * paramétrable serait de la généralité sans client pour l'exiger.
 */
@Configuration
public class ConfigurationHorloge {

	static final ZoneId ZONE_CLINIQUE = ZoneId.of("Europe/Paris");

	@Bean
	Clock horloge() {
		return Clock.system(ZONE_CLINIQUE);
	}
}
