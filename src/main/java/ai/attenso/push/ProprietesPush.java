package ai.attenso.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clés VAPID du service (EF-307).
 *
 * <p>Vides par défaut, et c'est un état normal : sans elles, le push est
 * simplement éteint et tout part en SMS. Même logique que l'expéditeur SMS non
 * encore enregistré — une fonction absente ne doit jamais empêcher la file de
 * tourner.
 *
 * @param sujet {@code mailto:} ou URL du service, exigé par la spécification
 *              VAPID pour que l'opérateur de push puisse joindre l'émetteur.
 */
@ConfigurationProperties("attenso.push")
public record ProprietesPush(String clePublique, String clePrivee, String sujet) {

	public boolean actif() {
		return clePublique != null && !clePublique.isBlank()
				&& clePrivee != null && !clePrivee.isBlank();
	}
}
