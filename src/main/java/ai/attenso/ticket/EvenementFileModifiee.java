package ai.attenso.ticket;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Publié dans la transaction, consommé après commit. Deux abonnés : la
 * diffusion SSE et la file d'envoi des SMS. Après commit, et pas avant : on ne
 * paie pas un SMS pour une transition annulée.
 */
public record EvenementFileModifiee(UUID tenantId, LocalDate jour) {
}
