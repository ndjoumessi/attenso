package ai.attenso.ticket;

/**
 * D'où vient un ticket. Sert à décider qui est soumis au plafond de dépense
 * quotidien, et la distinction n'est pas cosmétique.
 *
 * <p>Le plafond protège de l'argent — chaque ticket déclenche un SMS payant et
 * l'agrégateur n'offre aucune limite. Il n'a aucune raison de barrer la route à
 * quelqu'un d'authentifié qui est debout dans la clinique. L'appliquer à
 * l'accueil signifierait qu'un script tapant sur l'enregistrement public
 * empêche l'ASV d'insérer une urgence : le remède coûterait plus cher que le
 * mal.
 */
public enum Origine {

	/** Auto-enregistrement anonyme depuis le QR du comptoir. Soumis au plafond. */
	COMPTOIR,

	/** Saisie par l'accueil, authentifiée. Jamais bloquée. */
	ACCUEIL;

	public boolean soumiseAuPlafond() {
		return this == COMPTOIR;
	}
}
