package ai.attenso.pilotage;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les chiffres d'exploitation (EF-704). */
@Repository
public class TableauDeBordDao {

	private final JdbcTemplate jdbc;

	public TableauDeBordDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param zone le fuseau de la CLINIQUE, et non celui du serveur. Une heure
	 *        de pointe calculée en UTC placerait le pic de 14 h à 12 h l'été,
	 *        ce qui rendrait le tableau inutilisable pour décider d'une pause.
	 */
	public TableauDeBord pour(UUID tenantId, LocalDate debut, LocalDate fin, ZoneId zone) {
		String fuseau = zone.getId();

		long enFile = compter(tenantId, fin, "statut IN ('EN_ATTENTE','PREVENU','APPELE','ABSENT')");
		long enConsultation = compter(tenantId, fin, "statut = 'EN_CONSULTATION'");
		long departs = jdbc.queryForObject("""
				SELECT count(*) FROM ticket WHERE tenant_id = ? AND jour BETWEEN ? AND ?
				  AND statut = 'PARTI'
				""", Long.class, tenantId, debut, fin);
		long visites = jdbc.queryForObject("""
				SELECT count(*) FROM ticket WHERE tenant_id = ? AND jour BETWEEN ? AND ?
				""", Long.class, tenantId, debut, fin);

		// Seuls les tickets RÉELLEMENT appelés portent un retard mesurable :
		// inclure ceux qui attendent encore ferait baisser la moyenne à chaque
		// nouvelle arrivée, et le chiffre dirait le contraire de la réalité.
		Integer retard = jdbc.queryForObject("""
				SELECT round(avg(extract(epoch FROM (appele_le - cree_le)) / 60))::int
				FROM ticket
				WHERE tenant_id = ? AND jour BETWEEN ? AND ? AND appele_le IS NOT NULL
				""", Integer.class, tenantId, debut, fin);

		List<TableauDeBord.Creneau> creneaux = jdbc.query("""
				SELECT extract(hour FROM cree_le AT TIME ZONE ?)::int AS heure,
				       count(*) AS visites,
				       round(avg(extract(epoch FROM (appele_le - cree_le)) / 60))::int AS retard
				FROM ticket
				WHERE tenant_id = ? AND jour BETWEEN ? AND ?
				GROUP BY 1 ORDER BY 1
				""",
				(ligne, i) -> new TableauDeBord.Creneau(ligne.getInt("heure"),
						ligne.getLong("visites"),
						(Integer) ligne.getObject("retard")),
				fuseau, tenantId, debut, fin);

		return new TableauDeBord(enFile, enConsultation, retard, departs, visites, creneaux);
	}

	private long compter(UUID tenantId, LocalDate jour, String condition) {
		Long n = jdbc.queryForObject("SELECT count(*) FROM ticket WHERE tenant_id = ? "
				+ "AND jour = ? AND " + condition, Long.class, tenantId, jour);
		return n == null ? 0 : n;
	}
}
