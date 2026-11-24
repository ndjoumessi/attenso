package ai.attenso.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * @param rappelLe {@code null} pour une injection sans rappel prévu. Ne pas
 *        confondre avec « rappel en retard » : l'absence de date ne doit
 *        jamais produire d'alerte ni de SMS.
 */
public record DemandeVaccination(@NotBlank @Size(max = 80) String vaccin,
		@NotNull LocalDate faitLe, LocalDate rappelLe, @Size(max = 200) String note) {
}
