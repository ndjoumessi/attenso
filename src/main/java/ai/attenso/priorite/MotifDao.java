package ai.attenso.priorite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les motifs de priorité d'une clinique. */
@Repository
public class MotifDao {

	/** Les deux exemples du cahier des charges, posés à l'installation. */
	public static final List<String> DEFAUTS = List.of("Personne âgée", "Personne handicapée");

	private static final String COLONNES = "id, libelle, rang, ordre, actif";

	private final JdbcTemplate jdbc;

	public MotifDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Ceux que l'accueil peut choisir : les actifs, dans l'ordre voulu. */
	public List<MotifPriorite> actifs(UUID tenantId) {
		return jdbc.query("SELECT " + COLONNES + " FROM motif_priorite "
				+ "WHERE tenant_id = ? AND actif ORDER BY ordre, libelle",
				MotifDao::lire, tenantId);
	}

	/** Tous, actifs ou non : c'est l'écran de réglage qui les montre. */
	public List<MotifPriorite> tous(UUID tenantId) {
		return jdbc.query("SELECT " + COLONNES + " FROM motif_priorite "
				+ "WHERE tenant_id = ? ORDER BY ordre, libelle", MotifDao::lire, tenantId);
	}

	public Optional<MotifPriorite> actifParId(UUID tenantId, UUID id) {
		return jdbc.query("SELECT " + COLONNES + " FROM motif_priorite "
				+ "WHERE tenant_id = ? AND id = ? AND actif", MotifDao::lire, tenantId, id)
				.stream().findFirst();
	}

	public UUID ajouter(UUID tenantId, String libelle, short rang, short ordre) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO motif_priorite (id, tenant_id, libelle, rang, ordre)
				VALUES (?, ?, ?, ?, ?)
				""", id, tenantId, libelle.trim(), rang, ordre);
		return id;
	}

	/**
	 * Désactive plutôt que supprimer : les tickets déjà émis portent le
	 * libellé en clair, mais l'historique des réglages a une valeur quand on
	 * se demande pourquoi la file était dans cet ordre-là ce jour-là.
	 */
	public void desactiver(UUID tenantId, UUID id) {
		jdbc.update("UPDATE motif_priorite SET actif = false WHERE tenant_id = ? AND id = ?",
				tenantId, id);
	}

	/** Pose les motifs par défaut d'une clinique neuve. */
	public void poserLesDefauts(UUID tenantId) {
		short ordre = 1;
		for (String libelle : DEFAUTS) {
			ajouter(tenantId, libelle, (short) 5, ordre++);
		}
	}

	private static MotifPriorite lire(java.sql.ResultSet ligne, int i)
			throws java.sql.SQLException {
		return new MotifPriorite(ligne.getObject("id", UUID.class), ligne.getString("libelle"),
				ligne.getShort("rang"), ligne.getShort("ordre"), ligne.getBoolean("actif"));
	}
}
