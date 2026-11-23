package ai.attenso.web;

import ai.attenso.ticket.LigneFile;
import java.time.LocalDate;
import java.util.List;

/**
 * Le tableau de l'accueil.
 *
 * @param departs départs sans consultation du jour. Remonté dans la même
 *        réponse que la file parce que c'est le chiffre qui justifie
 *        l'abonnement : il doit être sous les yeux du gérant sans qu'il ait à
 *        le chercher.
 */
public record VueFile(LocalDate jour, List<LigneFile> lignes, long enFile, long departs,
		long attenteMedianeMinutes) {
}
