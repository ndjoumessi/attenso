package ai.attenso.agenda;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Le quota du créneau est atteint.
 *
 * <p>Cas normal d'une réservation concurrente : deux personnes cliquent sur la
 * dernière place, la seconde reçoit ce refus. Ce n'est pas une anomalie, et le
 * message doit inviter à choisir une autre heure plutôt qu'à réessayer.
 */
public class CreneauComplet extends RuntimeException {

	public CreneauComplet(LocalDate jour, LocalTime heure) {
		super("créneau complet : " + jour + " à " + heure);
	}
}
