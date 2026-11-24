package ai.attenso.ticket;

/**
 * Le rang de priorité d'un ticket, et la raison qui l'a produit (EF-407).
 *
 * <p>Les deux voyagent ensemble parce qu'un rang sans motif est inexplicable à
 * la salle d'attente : quand quelqu'un passe devant, l'ASV doit pouvoir dire
 * pourquoi. C'est aussi ce qui remplace un douzième paramètre dans le
 * constructeur de {@link Ticket}.
 *
 * <p>Le motif est un <strong>libellé copié</strong>, pas une référence : la
 * clinique peut renommer ou retirer un motif, le ticket déjà émis doit rester
 * lisible dans le journal et dans l'export.
 *
 * @param motif {@code null} pour la file normale et pour l'urgence — « urgence »
 *              est porté par le rang, le redire en texte serait redondant.
 */
public record Priorisation(short rang, String motif) {

	public static final Priorisation NORMALE = new Priorisation(Priorite.NORMALE, null);

	public static final Priorisation URGENCE = new Priorisation(Priorite.URGENCE, null);

	public Priorisation {
		if (rang < Priorite.NORMALE || rang > Priorite.URGENCE) {
			throw new IllegalArgumentException("rang de priorité hors bande : " + rang);
		}
		motif = (motif == null || motif.isBlank()) ? null : motif.trim();
	}

	/** Un motif choisi par l'accueil : toujours dans la bande intermédiaire. */
	public static Priorisation motivee(short rang, String motif) {
		return new Priorisation(rang, motif);
	}

	public boolean estUrgence() {
		return rang == Priorite.URGENCE;
	}
}
