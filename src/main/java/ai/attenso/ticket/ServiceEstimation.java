package ai.attenso.ticket;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durée d'attente estimée : médiane glissante des vingt dernières
 * consultations du tenant, multipliée par la position.
 *
 * <p>Médiane et non moyenne : une consultation de deux heures ne doit pas
 * décaler toute la salle. Auto-calibrée : aucun réglage à demander à la
 * clinique, qui n'en connaît pas la réponse de toute façon.
 */
@Service
public class ServiceEstimation {

	static final int ECHANTILLON = 20;

	static final int ECHANTILLONS_MINIMUM = 3;

	static final Duration DUREE_PAR_DEFAUT = Duration.ofMinutes(15);

	private static final double MARGE_BASSE = 0.8;

	private static final double MARGE_HAUTE = 1.2;

	private static final long PAS_ARRONDI_MINUTES = 5;

	private final TicketRepository tickets;

	public ServiceEstimation(TicketRepository tickets) {
		this.tickets = tickets;
	}

	@Transactional(readOnly = true)
	public Duration dureeMediane(UUID tenantId) {
		List<Double> secondes = tickets.dernieresDureesSecondes(tenantId, ECHANTILLON).stream()
				.filter(valeur -> valeur != null && valeur > 0)
				.sorted()
				.toList();
		if (secondes.size() < ECHANTILLONS_MINIMUM) {
			return DUREE_PAR_DEFAUT;
		}
		int milieu = secondes.size() / 2;
		double mediane = (secondes.size() % 2 == 1)
				? secondes.get(milieu)
				: (secondes.get(milieu - 1) + secondes.get(milieu)) / 2.0;
		return Duration.ofSeconds(Math.round(mediane));
	}

	public long dureeMedianeMinutes(UUID tenantId) {
		return Math.max(1, dureeMediane(tenantId).toMinutes());
	}

	/** Fourchette pour une position donnée. Position 1 = le prochain appelé. */
	public Fourchette fourchette(UUID tenantId, int position) {
		long attendues = Math.max(0, position - 1);
		double minutes = dureeMediane(tenantId).toMinutes() * (double) attendues;
		return new Fourchette(
				arrondirVersLeBas(minutes * MARGE_BASSE),
				Math.max(PAS_ARRONDI_MINUTES, arrondirVersLeHaut(minutes * MARGE_HAUTE)));
	}

	private long arrondirVersLeBas(double minutes) {
		return (long) (Math.floor(minutes / PAS_ARRONDI_MINUTES) * PAS_ARRONDI_MINUTES);
	}

	private long arrondirVersLeHaut(double minutes) {
		return (long) (Math.ceil(minutes / PAS_ARRONDI_MINUTES) * PAS_ARRONDI_MINUTES);
	}
}
