package ai.attenso.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Un créneau réel, à une date, avec son quota. */
public record Creneau(UUID id, LocalDate jour, LocalTime heure, short places, short prises) {

	public boolean complet() {
		return prises >= places;
	}

	public int restantes() {
		return Math.max(0, places - prises);
	}
}
