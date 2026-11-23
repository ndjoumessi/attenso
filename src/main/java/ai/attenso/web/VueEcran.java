package ai.attenso.web;

import java.util.List;

/**
 * L'écran de la salle d'attente. Ne porte que des numéros : aucune donnée
 * personnelle n'est affichée au mur, donc rien à protéger au-delà du slug de
 * la clinique — c'est pourquoi il n'y a pas de jeton d'appairage.
 */
public record VueEcran(String clinique, List<Integer> appeles, List<Integer> prochains,
		long attenteMedianeMinutes) {
}
