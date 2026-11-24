package ai.attenso.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** @param jourSemaine 1 = lundi … 7 = dimanche, comme java.time.DayOfWeek. */
public record DemandePlage(@Min(1) @Max(7) int jourSemaine,
		@NotNull @JsonFormat(pattern = "HH:mm") LocalTime debut,
		@NotNull @JsonFormat(pattern = "HH:mm") LocalTime fin,
		@Min(5) @Max(240) short dureeMinutes,
		@Min(1) @Max(20) short places) {
}
