package ai.attenso.agenda;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Une tranche d'ouverture hebdomadaire, découpée en créneaux (EF-905).
 *
 * @param places nombre de rendez-vous simultanés possibles sur un créneau :
 *               deux salles de consultation, c'est deux places à 9 h 00.
 */
public record PlageHoraire(UUID id, DayOfWeek jour, LocalTime debut, LocalTime fin,
		short dureeMinutes, short places, boolean actif) {
}
