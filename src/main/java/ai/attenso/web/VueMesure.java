package ai.attenso.web;

import java.time.LocalDate;

/**
 * @param taux  {@code null} tant que l'accueil n'a pas saisi les arrivants.
 *              L'interface doit alors demander la saisie, et surtout ne pas
 *              afficher 0 %.
 * @param zone  le verdict du cahier des charges §12 : INVALIDE sous 30 %,
 *              ZONE_GRISE jusqu'à 50 %, VALIDE au-delà.
 */
public record VueMesure(LocalDate jour, Integer arrivantsTotal, long ticketsComptoir,
		long ticketsAccueil, long ticketsTotal, Integer taux, String zone, long departs) {
}
