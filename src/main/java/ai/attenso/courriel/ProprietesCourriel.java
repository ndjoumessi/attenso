package ai.attenso.courriel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * De qui partent les courriels, et s'il y a de quoi les envoyer.
 *
 * <p>Le serveur d'envoi lui-même est configuré par les réglages standard de
 * Spring ({@code spring.mail.*}) : il n'y a aucune raison d'en réinventer les
 * noms. Ne vit ici que ce qui est propre à Attenso — l'adresse d'expédition.
 *
 * @param expediteur adresse « De : ». Sans elle, l'envoi est éteint, et la
 *        récupération de mot de passe avec lui.
 */
@ConfigurationProperties("attenso.courriel")
public record ProprietesCourriel(String expediteur, String hote) {

	/**
	 * Vrai quand un courriel peut réellement partir.
	 *
	 * <p>L'hôte SMTP vient de {@code spring.mail.host} et est recopié ici par
	 * la configuration : sans lui, Spring enverrait vers « localhost » et
	 * l'échec n'apparaîtrait qu'au premier envoi, c'est-à-dire le jour où
	 * quelqu'un a vraiment perdu son mot de passe.
	 */
	public boolean actif() {
		return rempli(expediteur) && rempli(hote);
	}

	private static boolean rempli(String valeur) {
		return valeur != null && !valeur.isBlank();
	}
}
