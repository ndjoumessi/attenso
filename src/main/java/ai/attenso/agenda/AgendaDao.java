package ai.attenso.agenda;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Plages, créneaux et rendez-vous (EF-905). */
@Repository
public class AgendaDao {

	private final JdbcTemplate jdbc;

	public AgendaDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// --- Plages hebdomadaires -------------------------------------------

	public UUID ajouterPlage(UUID tenantId, DayOfWeek jour, LocalTime debut, LocalTime fin,
			short dureeMinutes, short places) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO plage_horaire (id, tenant_id, jour_semaine, debut, fin,
				                           duree_minutes, places)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, tenantId, (short) jour.getValue(), debut, fin, dureeMinutes, places);
		return id;
	}

	public List<PlageHoraire> plages(UUID tenantId) {
		return jdbc.query("""
				SELECT * FROM plage_horaire WHERE tenant_id = ? AND actif
				ORDER BY jour_semaine, debut
				""",
				(l, i) -> new PlageHoraire(l.getObject("id", UUID.class),
						DayOfWeek.of(l.getShort("jour_semaine")),
						l.getTime("debut").toLocalTime(), l.getTime("fin").toLocalTime(),
						l.getShort("duree_minutes"), l.getShort("places"),
						l.getBoolean("actif")),
				tenantId);
	}

	// --- Créneaux --------------------------------------------------------

	/**
	 * Matérialise un créneau s'il n'existe pas encore.
	 *
	 * <p>{@code ON CONFLICT DO NOTHING} : la génération peut repasser sur une
	 * journée déjà produite sans écraser les réservations qu'elle porte. Le
	 * quota d'un créneau né hier reste celui d'hier, même si la clinique a
	 * changé sa grille depuis.
	 */
	public void materialiser(UUID tenantId, LocalDate jour, LocalTime heure, short places) {
		jdbc.update("""
				INSERT INTO creneau (tenant_id, jour, heure, places) VALUES (?, ?, ?, ?)
				ON CONFLICT (tenant_id, jour, heure) DO NOTHING
				""", tenantId, jour, heure, places);
	}

	public List<Creneau> creneaux(UUID tenantId, LocalDate debut, LocalDate fin) {
		return jdbc.query("""
				SELECT * FROM creneau WHERE tenant_id = ? AND jour BETWEEN ? AND ?
				ORDER BY jour, heure
				""", AgendaDao::lireCreneau, tenantId, debut, fin);
	}

	/**
	 * Réserve une place, atomiquement.
	 *
	 * <p>Le {@code WHERE prises < places} est tout le mécanisme : compter puis
	 * insérer laisserait passer ensemble toutes les réservations ayant lu le
	 * compteur avant qu'aucune ne l'incrémente, et deux personnes auraient le
	 * même créneau dans une clinique qui n'a qu'une salle.
	 *
	 * @return l'identifiant du créneau si une place a été prise.
	 */
	public Optional<UUID> reserverUnePlace(UUID tenantId, LocalDate jour, LocalTime heure) {
		List<UUID> pris = jdbc.queryForList("""
				UPDATE creneau SET prises = prises + 1
				WHERE tenant_id = ? AND jour = ? AND heure = ? AND prises < places
				RETURNING id
				""", UUID.class, tenantId, jour, heure);
		return pris.stream().findFirst();
	}

	/** Rend la place d'un rendez-vous annulé : le créneau redevient réservable. */
	public void rendreUnePlace(UUID tenantId, UUID creneauId) {
		jdbc.update("""
				UPDATE creneau SET prises = greatest(0, prises - 1)
				WHERE tenant_id = ? AND id = ?
				""", tenantId, creneauId);
	}

	// --- Rendez-vous -----------------------------------------------------

	public void poser(UUID tenantId, UUID id, UUID creneauId, LocalDate jour, LocalTime heure,
			String reference, String telephoneE164, String motif) {
		jdbc.update("""
				INSERT INTO rendez_vous (id, tenant_id, creneau_id, jour, heure, reference,
				                         telephone_e164, motif)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", id, tenantId, creneauId, jour, heure, reference, telephoneE164, motif);
	}

	public Optional<RendezVous> parReference(UUID tenantId, String reference) {
		return jdbc.query("SELECT * FROM rendez_vous WHERE tenant_id = ? AND reference = ?",
				AgendaDao::lireRendezVous, tenantId, reference).stream().findFirst();
	}

	public List<RendezVous> duJour(UUID tenantId, LocalDate jour) {
		return jdbc.query("""
				SELECT * FROM rendez_vous WHERE tenant_id = ? AND jour = ?
				ORDER BY heure, cree_le
				""", AgendaDao::lireRendezVous, tenantId, jour);
	}

	/** Les rendez-vous de demain qui n'ont pas encore reçu leur rappel. */
	public List<RendezVous> aRappeler(UUID tenantId, LocalDate jour) {
		return jdbc.query("""
				SELECT * FROM rendez_vous
				WHERE tenant_id = ? AND jour = ? AND statut = 'CONFIRME'
				  AND rappel_envoye_le IS NULL AND telephone_e164 IS NOT NULL
				ORDER BY heure
				""", AgendaDao::lireRendezVous, tenantId, jour);
	}

	/**
	 * Marque le rappel envoyé, et ne le fait qu'une fois.
	 *
	 * @return {@code false} si un autre fil l'a marqué entre-temps. C'est le
	 *         garde-fou contre le double rappel : deux instances qui balayent
	 *         en même temps n'enverraient qu'un seul SMS.
	 */
	public boolean marquerRappele(UUID tenantId, UUID id, java.time.Instant quand) {
		return jdbc.update("""
				UPDATE rendez_vous SET rappel_envoye_le = ?
				WHERE tenant_id = ? AND id = ? AND rappel_envoye_le IS NULL
				""", java.sql.Timestamp.from(quand), tenantId, id) > 0;
	}

	public Optional<UUID> annuler(UUID tenantId, String reference) {
		List<UUID> creneaux = jdbc.queryForList("""
				UPDATE rendez_vous SET statut = 'ANNULE'
				WHERE tenant_id = ? AND reference = ? AND statut = 'CONFIRME'
				RETURNING creneau_id
				""", UUID.class, tenantId, reference);
		return creneaux.stream().findFirst();
	}

	private static Creneau lireCreneau(ResultSet l, int i) throws SQLException {
		return new Creneau(l.getObject("id", UUID.class), l.getDate("jour").toLocalDate(),
				l.getTime("heure").toLocalTime(), l.getShort("places"), l.getShort("prises"));
	}

	private static RendezVous lireRendezVous(ResultSet l, int i) throws SQLException {
		var rappel = l.getTimestamp("rappel_envoye_le");
		return new RendezVous(l.getObject("id", UUID.class), l.getString("reference"),
				l.getDate("jour").toLocalDate(), l.getTime("heure").toLocalTime(),
				l.getString("telephone_e164"), l.getString("motif"), l.getString("statut"),
				rappel == null ? null : rappel.toInstant(),
				l.getTimestamp("cree_le").toInstant());
	}
}
