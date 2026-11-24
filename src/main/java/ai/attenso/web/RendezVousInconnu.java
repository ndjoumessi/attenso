package ai.attenso.web;

/**
 * Référence de rendez-vous inconnue.
 *
 * <p>Même traitement qu'une référence de ticket inconnue : un code, un
 * message, rien à déduire. Distinguer « n'existe pas » de « annulé » donnerait
 * un oracle à qui essaie des références au hasard.
 */
public class RendezVousInconnu extends RuntimeException {

	public RendezVousInconnu() {
		super("rendez-vous inconnu");
	}
}
