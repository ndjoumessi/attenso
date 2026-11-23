package ai.attenso.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Câblage des clients HTTP d'Octopush, séparé des adaptateurs.
 *
 * <p>Séparé pour une raison concrète : un adaptateur qui pose lui-même sa
 * fabrique de requêtes ne peut pas être testé contre un serveur simulé — la
 * fabrique du test se ferait écraser et les tests partiraient sur le vrai
 * réseau, chez un fournisseur qui facture.
 *
 * <p><strong>Deux clients, et pas un seul</strong>, parce que les deux appels
 * n'ont pas le même budget de temps. Le HLR est sur le chemin critique de
 * quelqu'un debout au comptoir : au-delà de quelques secondes, mieux vaut
 * émettre le ticket sans savoir. L'envoi de SMS, lui, tourne en arrière-plan et
 * peut attendre.
 *
 * <p>La condition couvre les deux usages : on peut vouloir le HLR sans l'envoi
 * réel, par exemple pour mesurer la qualité des numéros avant d'ouvrir les SMS.
 *
 * <p>Les clients sont construits par {@code RestClient.builder()} et non à
 * partir d'un {@code RestClient.Builder} injecté : ce bean n'est pas
 * auto-configuré ici, et en dépendre faisait échouer le démarrage dès que l'un
 * des deux réglages passait sur Octopush. Rien dans ce projet n'a besoin des
 * personnalisations globales qu'il apporterait.
 */
@Configuration
@ConditionalOnExpression("'${attenso.sms.expediteur:journal}'.equals('octopush')"
		+ " or '${attenso.hlr.verificateur:format}'.equals('octopush')")
public class ConfigurationOctopush {

	private static final Duration CONNEXION = Duration.ofSeconds(5);

	private static final Duration LECTURE_SMS = Duration.ofSeconds(15);

	private static final Duration LECTURE_HLR = Duration.ofSeconds(3);

	/**
	 * Exige les identifiants ici plutôt que par validation déclarative : les
	 * propriétés se chargent aussi quand Octopush n'est pas utilisé, où des
	 * valeurs vides sont normales. Et le message nomme la variable manquante,
	 * ce qu'une trace de validation ne fait pas.
	 */
	private static void exiger(ProprietesOctopush proprietes) {
		if (proprietes.login() == null || proprietes.login().isBlank()) {
			throw new IllegalStateException("ATTENSO_OCTOPUSH_LOGIN est obligatoire "
					+ "dès qu'un des deux réglages Octopush est actif");
		}
		if (proprietes.cle() == null || proprietes.cle().isBlank()) {
			throw new IllegalStateException("ATTENSO_OCTOPUSH_CLE est obligatoire "
					+ "dès qu'un des deux réglages Octopush est actif");
		}
	}

	@Bean
	RestClient clientOctopushSms(ProprietesOctopush proprietes) {
		return construire(proprietes, ou(proprietes.delaiLecture(), LECTURE_SMS));
	}

	@Bean
	RestClient clientOctopushHlr(ProprietesOctopush proprietes, ProprietesHlr hlr) {
		return construire(proprietes, ou(hlr.delaiLecture(), LECTURE_HLR));
	}

	private RestClient construire(ProprietesOctopush proprietes, Duration lecture) {
		exiger(proprietes);
		SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
		fabrique.setConnectTimeout(ou(proprietes.delaiConnexion(), CONNEXION));
		fabrique.setReadTimeout(lecture);
		return RestClient.builder()
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
