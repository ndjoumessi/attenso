package ai.attenso.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param telephone mobile du propriétaire, pour les rappels de vaccin. Il
 *        n'est PAS purgé à J+1 comme celui d'un ticket : un rappel se
 *        déclenche des mois plus tard. Deux finalités, deux durées.
 */
public record DemandeAnimal(@NotBlank @Size(max = 80) String nom,
		@NotBlank @Size(max = 40) String espece,
		@Size(max = 24) String telephone,
		boolean avecCarnet) {
}
