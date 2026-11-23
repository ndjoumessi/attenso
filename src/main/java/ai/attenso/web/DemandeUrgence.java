package ai.attenso.web;

import jakarta.validation.constraints.Size;

public record DemandeUrgence(@Size(max = 24) String telephone) {
}
