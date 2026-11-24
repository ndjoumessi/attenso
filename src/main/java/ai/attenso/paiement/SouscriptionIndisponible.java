package ai.attenso.paiement;

/**
 * La souscription en ligne n'est pas configurée, ou Stripe n'a pas répondu.
 *
 * <p>Refuser l'inscription plutôt que créer la clinique quand même : une
 * clinique créée sans abonnement est une clinique qui travaille gratuitement
 * sans que personne ne l'ait décidé, et qu'il faudra réconcilier à la main.
 */
public class SouscriptionIndisponible extends RuntimeException {

	public SouscriptionIndisponible(String message) {
		super(message);
	}

	public SouscriptionIndisponible(String message, Throwable cause) {
		super(message, cause);
	}
}
