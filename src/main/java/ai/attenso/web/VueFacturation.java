package ai.attenso.web;

/**
 * @param palier         {@code null} pendant le pilote, qui est gratuit.
 * @param palierConseille {@code null} quand le palier actuel est le bon. Il
 *        n'est rempli que s'il y a vraiment un changement à faire : une
 *        clinique qui dépasse tous les mois paie plus cher en dépassement
 *        qu'en changeant de formule, et le lui cacher serait un piège.
 */
public record VueFacturation(String palier, int visitesIncluses, int prixCentimes,
		int visitesDuMois, int depassementCentimes, String palierConseille) {
}
