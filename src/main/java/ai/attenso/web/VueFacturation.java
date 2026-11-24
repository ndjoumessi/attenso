package ai.attenso.web;

/**
 * @param palier         {@code null} pendant le pilote, qui est gratuit.
 * @param gerable vrai s'il existe un abonnement chez le prestataire de
 *        paiement, donc un espace client à ouvrir. Faux pour une clinique
 *        créée avant la mise en vente, ou dont le paiement n'a jamais abouti :
 *        mieux vaut cacher le bouton que d'en afficher un qui s'excuse.
 * @param palierConseille {@code null} quand le palier actuel est le bon. Il
 *        n'est rempli que s'il y a vraiment un changement à faire : une
 *        clinique qui dépasse tous les mois paie plus cher en dépassement
 *        qu'en changeant de formule, et le lui cacher serait un piège.
 */
public record VueFacturation(String palier, int visitesIncluses, int prixCentimes,
		int visitesDuMois, int depassementCentimes, String palierConseille,
		boolean gerable) {
}
