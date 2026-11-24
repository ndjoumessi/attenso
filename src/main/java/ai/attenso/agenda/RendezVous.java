package ai.attenso.agenda;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @param reference référence opaque, même objet que celle d'un ticket : elle
 *        vaut justificatif pour consulter ou annuler, sans compte ni mot de
 *        passe. Contrairement à celle d'un ticket, elle NE MEURT PAS avec la
 *        journée — un rendez-vous se prend des semaines à l'avance, et le lien
 *        doit encore marcher la veille.
 */
public record RendezVous(UUID id, String reference, LocalDate jour, LocalTime heure,
		String telephoneE164, String motif, String statut, Instant rappelEnvoyeLe,
		Instant creeLe) {
}
