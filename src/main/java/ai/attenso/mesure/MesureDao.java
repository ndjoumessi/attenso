package ai.attenso.mesure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecture et saisie des indicateurs du pilote. */
@Repository
public class MesureDao {

	private final JdbcTemplate jdbc;

	public MesureDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Saisie ou correction du nombre d'arrivants d'une journée. */
	public void poserArrivants(UUID tenantId, LocalDate jour, int arrivants) {
		jdbc.update("""
				INSERT INTO mesure_jour (tenant_id, jour, arrivants_total)
				VALUES (?, ?, ?)
				ON CONFLICT (tenant_id, jour)
				DO UPDATE SET arrivants_total = EXCLUDED.arrivants_total, saisi_le = now()
				""", tenantId, jour, arrivants);
	}

	public MesureJour duJour(UUID tenantId, LocalDate jour) {
		List<MesureJour> trouve = surPeriode(tenantId, jour, jour);
		return trouve.isEmpty()
				? new MesureJour(jour, null, 0, 0, 0)
				: trouve.get(0);
	}

	/**
	 * Une ligne par jour où il s'est passé quelque chose, dans un sens comme
	 * dans l'autre.
	 *
	 * <p><strong>Jointure pleine, et c'est le cœur du calcul.</strong> Les deux
	 * côtés manquent pour de bonnes raisons, et les deux cas sont justement
	 * ceux qu'il faut voir :
	 *
	 * <ul>
	 * <li>des tickets sans saisie d'arrivants : l'ASV a oublié de compter, le
	 * taux est absent — pas nul ;</li>
	 * <li>une saisie d'arrivants sans aucun ticket : la journée où le QR code
	 * était cassé, ou l'affiche décrochée. C'est le 0 % le plus éloquent du
	 * pilote. Une jointure partant des tickets l'effacerait purement et
	 * simplement, et effacerait du même coup la saisie du matin dès que
	 * l'écran est ouvert avant le premier client.</li>
	 * </ul>
	 */
	public List<MesureJour> surPeriode(UUID tenantId, LocalDate debut, LocalDate fin) {
		return jdbc.query("""
				SELECT COALESCE(t.jour, m.jour)  AS jour,
				       m.arrivants_total,
				       COALESCE(t.comptoir, 0)   AS comptoir,
				       COALESCE(t.accueil, 0)    AS accueil,
				       COALESCE(t.departs, 0)    AS departs
				FROM (
				    SELECT jour,
				           count(*) FILTER (WHERE origine = 'COMPTOIR') AS comptoir,
				           count(*) FILTER (WHERE origine = 'ACCUEIL')  AS accueil,
				           count(*) FILTER (WHERE statut  = 'PARTI')    AS departs
				    FROM ticket
				    WHERE tenant_id = ? AND jour BETWEEN ? AND ?
				    GROUP BY jour
				) t
				FULL OUTER JOIN (
				    SELECT jour, arrivants_total
				    FROM mesure_jour
				    WHERE tenant_id = ? AND jour BETWEEN ? AND ?
				) m ON m.jour = t.jour
				ORDER BY 1
				""",
				(ligne, i) -> new MesureJour(
						ligne.getDate("jour").toLocalDate(),
						(Integer) ligne.getObject("arrivants_total"),
						ligne.getLong("comptoir"),
						ligne.getLong("accueil"),
						ligne.getLong("departs")),
				tenantId, debut, fin, tenantId, debut, fin);
	}
}
