package ai.attenso.ticket;

/**
 * Plafond de tickets d'une clinique pour la journée. Se traduit en HTTP 429.
 *
 * <p>C'est un garde-fou de dépense, pas une règle de salle d'attente : il est
 * réglé bien au-dessus d'une journée réelle. S'il se déclenche, soit la
 * clinique a changé d'échelle, soit quelqu'un boucle sur l'enregistrement — et
 * dans les deux cas il faut le savoir avant de le lire sur la facture.
 */
public class PlafondJournalierAtteint extends RuntimeException {

	public PlafondJournalierAtteint(int plafond) {
		super("plafond de " + plafond + " tickets pour la journée atteint");
	}
}
