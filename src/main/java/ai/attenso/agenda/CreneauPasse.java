package ai.attenso.agenda;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Le créneau demandé est déjà passé.
 *
 * <p>Distinct de {@link CreneauComplet} parce que la cause est différente et le
 * remède aussi : complet veut dire « choisissez une autre heure », passé veut
 * dire « cette page est périmée, rechargez ». Le cas normal est une page
 * laissée ouverte jusqu'au lendemain ; le cas qui compte est un envoi direct
 * sur l'API, que le filtre d'affichage ne voyait pas.
 */
public class CreneauPasse extends RuntimeException {

	public CreneauPasse(LocalDate jour, LocalTime heure) {
		super("créneau déjà passé : " + jour + " à " + heure);
	}
}
