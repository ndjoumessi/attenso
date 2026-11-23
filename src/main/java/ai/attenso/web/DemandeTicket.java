package ai.attenso.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemandeTicket(@NotBlank @Size(max = 24) String telephone) {
}
