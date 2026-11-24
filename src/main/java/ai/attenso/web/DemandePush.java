package ai.attenso.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * L'abonnement push tel que le navigateur le produit (EF-307).
 *
 * <p>Les tailles sont bornées : ces champs viennent d'un point d'entrée
 * anonyme, et un endpoint est une URL d'opérateur, pas un champ libre.
 */
public record DemandePush(@NotBlank @Size(max = 1024) String endpoint,
		@NotBlank @Size(max = 256) String clePublique,
		@NotBlank @Size(max = 256) String cleAuth) {
}
