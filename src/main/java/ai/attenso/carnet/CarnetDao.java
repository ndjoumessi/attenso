package ai.attenso.carnet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Animaux et vaccinations (EF-906). */
@Repository
public class CarnetDao {

	private final JdbcTemplate jdbc;

	public CarnetDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// --- Animaux ---------------------------------------------------------

	public UUID creer(UUID tenantId, String nom, String espece, String telephoneE164,
			String jeton, Instant quand) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO animal (id, tenant_id, nom, espece, telephone_e164, jeton_carnet,
				                    jeton_pose_le)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, tenantId, nom.trim(), espece, telephoneE164, jeton,
				jeton == null ? null : java.sql.Timestamp.from(quand));
		return id;
	}

	public Optional<Animal> parId(UUID tenantId, UUID id) {
		return jdbc.query("SELECT * FROM animal WHERE tenant_id = ? AND id = ?",
				CarnetDao::lireAnimal, tenantId, id).stream().findFirst();
	}

	public Optional<Animal> parJeton(UUID tenantId, String jeton) {
		return jdbc.query("""
				SELECT * FROM animal WHERE tenant_id = ? AND jeton_carnet = ? AND actif
				""", CarnetDao::lireAnimal, tenantId, jeton).stream().findFirst();
	}

	public List<Animal> tous(UUID tenantId) {
		return jdbc.query("SELECT * FROM animal WHERE tenant_id = ? AND actif ORDER BY nom",
				CarnetDao::lireAnimal, tenantId);
	}

	/**
	 * Révoque le QR en cours et en pose un neuf, ou aucun.
	 *
	 * <p>Rotation plutôt que suppression : un QR imprimé sur une médaille se
	 * photographie, et le propriétaire doit pouvoir couper l'accès <em>sans</em>
	 * qu'on lui refasse sa médaille — il en recolle une neuve, c'est tout.
	 * Passer {@code null} ferme le carnet définitivement.
	 */
	public void remplacerJeton(UUID tenantId, UUID animalId, String nouveau, Instant quand) {
		jdbc.update("""
				UPDATE animal SET jeton_carnet = ?, jeton_pose_le = ?
				WHERE tenant_id = ? AND id = ?
				""", nouveau, nouveau == null ? null : java.sql.Timestamp.from(quand),
				tenantId, animalId);
	}

	// --- Vaccinations ----------------------------------------------------

	public UUID ajouterVaccination(UUID tenantId, UUID animalId, String vaccin,
			LocalDate faitLe, LocalDate rappelLe, String note) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO vaccination (id, tenant_id, animal_id, vaccin, fait_le, rappel_le,
				                         note)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, tenantId, animalId, vaccin.trim(), faitLe, rappelLe, note);
		return id;
	}

	public List<Vaccination> vaccinations(UUID tenantId, UUID animalId) {
		return jdbc.query("""
				SELECT * FROM vaccination WHERE tenant_id = ? AND animal_id = ?
				ORDER BY fait_le DESC
				""", CarnetDao::lireVaccination, tenantId, animalId);
	}

	/**
	 * Les rappels échéant au plus tard à la date donnée, non encore envoyés.
	 *
	 * <p>« Au plus tard » et non « exactement ce jour-là » : une journée où la
	 * tâche n'a pas tourné — redémarrage, panne — ne doit pas faire sauter
	 * définitivement les rappels de ce jour.
	 */
	public List<RappelDu> rappelsDus(UUID tenantId, LocalDate limite) {
		return jdbc.query("""
				SELECT v.id, v.animal_id, a.telephone_e164, a.jeton_carnet
				FROM vaccination v JOIN animal a ON a.id = v.animal_id
				WHERE v.tenant_id = ? AND v.rappel_le IS NOT NULL AND v.rappel_le <= ?
				  AND v.rappel_envoye_le IS NULL AND a.actif
				  AND a.telephone_e164 IS NOT NULL
				ORDER BY v.rappel_le
				""",
				(l, i) -> new RappelDu(l.getObject("id", UUID.class),
						l.getObject("animal_id", UUID.class), l.getString("telephone_e164"),
						l.getString("jeton_carnet")),
				tenantId, limite);
	}

	/** @return faux si un autre fil l'a marqué : garde-fou contre le double rappel. */
	public boolean marquerRappele(UUID tenantId, UUID vaccinationId, Instant quand) {
		return jdbc.update("""
				UPDATE vaccination SET rappel_envoye_le = ?
				WHERE tenant_id = ? AND id = ? AND rappel_envoye_le IS NULL
				""", java.sql.Timestamp.from(quand), tenantId, vaccinationId) > 0;
	}

	public record RappelDu(UUID vaccinationId, UUID animalId, String telephoneE164,
			String jetonCarnet) {
	}

	private static Animal lireAnimal(ResultSet l, int i) throws SQLException {
		var pose = l.getTimestamp("jeton_pose_le");
		return new Animal(l.getObject("id", UUID.class), l.getString("nom"),
				l.getString("espece"), l.getString("telephone_e164"),
				l.getString("jeton_carnet"), pose == null ? null : pose.toInstant(),
				l.getBoolean("actif"));
	}

	private static Vaccination lireVaccination(ResultSet l, int i) throws SQLException {
		var rappel = l.getDate("rappel_le");
		var envoye = l.getTimestamp("rappel_envoye_le");
		return new Vaccination(l.getObject("id", UUID.class),
				l.getObject("animal_id", UUID.class), l.getString("vaccin"),
				l.getDate("fait_le").toLocalDate(),
				rappel == null ? null : rappel.toLocalDate(),
				envoye == null ? null : envoye.toInstant(), l.getString("note"));
	}
}
