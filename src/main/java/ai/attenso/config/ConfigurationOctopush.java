package ai.attenso.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Câblage du client HTTP d'Octopush, séparé de l'adaptateur.
 *
 * <p>Séparé pour une raison concrète : un adaptateur qui pose lui-même sa
 * fabrique de requêtes ne peut pas être testé contre un serveur simulé — la
 * fabrique du test se ferait écraser et les tests partiraient sur le vrai
 * réseau, chez un fournisseur qui facture.
 */
@Configuration
@ConditionalOnProperty(name = "attenso.sms.expediteur", havingValue = "octopush")
public class ConfigurationOctopush {

	private static final Duration CONNEXION_PAR_DEFAUT = Duration.ofSeconds(5);

	private static final Duration LECTURE_PAR_DEFAUT = Duration.ofSeconds(15);

	/**
	 * Sans délais explicites, un agrégateur qui ne répond plus bloquerait le
	 * fil d'envoi indéfiniment, et avec lui les SMS de toutes les cliniques.
	 */
	@Bean
	RestClient clientOctopush(RestClient.Builder constructeur, ProprietesOctopush proprietes) {
		SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
		fabrique.setConnectTimeout(ou(proprietes.delaiConnexion(), CONNEXION_PAR_DEFAUT));
		fabrique.setReadTimeout(ou(proprietes.delaiLecture(), LECTURE_PAR_DEFAUT));
		return constructeur
				.baseUrl(proprietes.url())
				.requestFactory(fabrique)
				.defaultHeader("api-login", proprietes.login())
				.defaultHeader("api-key", proprietes.cle())
				.build();
	}

	private static Duration ou(Duration configure, Duration parDefaut) {
		return configure != null ? configure : parDefaut;
	}
}
