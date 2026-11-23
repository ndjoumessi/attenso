package ai.attenso.web;

import jakarta.validation.constraints.NotBlank;

public record DemandeConnexion(@NotBlank String slug, @NotBlank String email,
		@NotBlank String motDePasse) {
}
