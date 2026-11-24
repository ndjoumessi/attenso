package ai.attenso.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @param telephone facultatif, comme partout : quelqu'un sans mobile doit
 *        pouvoir prendre rendez-vous. Il ne recevra simplement pas le rappel.
 */
public record DemandeRendezVous(@NotNull LocalDate jour,
		@NotNull @JsonFormat(pattern = "HH:mm") LocalTime heure,
		@Size(max = 24) String telephone,
		@Size(max = 120) String motif) {
}
