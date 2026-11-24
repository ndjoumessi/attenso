package ai.attenso.ticket;

import java.time.Instant;
import java.util.UUID;

/** Une ligne du tableau de l'accueil. La position est calculée, jamais lue. */
public record LigneFile(UUID id, int numeroJour, int position, Statut statut, short priorite,
		Instant creeLe, Instant appeleLe, Instant graceExpireLe, boolean joignable,
		String motifPriorite, Instant signaleProcheLe) {
}
