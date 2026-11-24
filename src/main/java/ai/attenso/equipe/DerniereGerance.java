package ai.attenso.equipe;

/**
 * Refus de retirer le dernier gérant actif d'une clinique.
 *
 * <p>Sans ce garde, une clinique peut se verrouiller dehors en un clic : plus
 * personne pour inviter, pour changer un rôle, ni pour gérer l'abonnement. La
 * sortie serait une intervention manuelle en base, c'est-à-dire un appel au
 * support un samedi.
 */
public class DerniereGerance extends RuntimeException {

	public DerniereGerance() {
		super("il doit rester au moins un gérant actif");
	}
}
