package ai.attenso.carnet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param rappelLe {@code null} pour une injection sans rappel prévu. Ne pas
 *        confondre avec « rappel en retard » : l'absence de date ne doit
 *        jamais produire d'alerte.
 */
public record Vaccination(UUID id, UUID animalId, String vaccin, LocalDate faitLe,
		LocalDate rappelLe, Instant rappelEnvoyeLe, String note) {

	/** Un rappel dépassé, à la date donnée. */
	public boolean enRetard(LocalDate aujourdhui) {
		return rappelLe != null && rappelLe.isBefore(aujourdhui);
	}
}
