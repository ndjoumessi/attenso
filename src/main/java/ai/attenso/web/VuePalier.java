package ai.attenso.web;

/** Un palier d'abonnement tel que la page d'inscription l'affiche (EF-902). */
public record VuePalier(String nom, int visitesIncluses, int prixCentimes,
		int depassementCentimes) {
}
