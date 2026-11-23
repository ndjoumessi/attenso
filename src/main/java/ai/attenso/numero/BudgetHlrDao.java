package ai.attenso.numero;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Réservation du budget quotidien d'appels payants.
 *
 * <p>Un {@code UPDATE ... WHERE appels < max} est indivisible : PostgreSQL
 * verrouille la ligne, et deux requêtes simultanées s'exécutent l'une après
 * l'autre. Compter puis appeler, à l'inverse, laissait passer d'un seul coup
 * autant de requêtes qu'on peut en lancer en parallèle — toutes ayant lu le
 * compteur avant qu'aucune ne l'incrémente.
 */
@Repository
public class BudgetHlrDao {

	private final JdbcTemplate jdbc;

	public BudgetHlrDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Prend une unité de budget, ou rend faux s'il est épuisé.
	 *
	 * <p>La réservation n'est <strong>pas</strong> rendue si l'appel échoue
	 * ensuite : un délai dépassé peut très bien correspondre à une requête que
	 * l'opérateur a reçue et facturée. Mieux vaut sur-compter une dépense que
	 * la sous-compter — c'est un plafond, pas une comptabilité.
	 */
	public boolean reserver(UUID tenantId, LocalDate jour, int maximum) {
		jdbc.update("""
				INSERT INTO budget_hlr (tenant_id, jour) VALUES (?, ?)
				ON CONFLICT (tenant_id, jour) DO NOTHING
				""", tenantId, jour);
		return jdbc.update("""
				UPDATE budget_hlr SET appels = appels + 1
				WHERE tenant_id = ? AND jour = ? AND appels < ?
				""", tenantId, jour, maximum) == 1;
	}

	public int consommeDuJour(UUID tenantId, LocalDate jour) {
		Integer appels = jdbc.queryForObject("""
				SELECT appels FROM budget_hlr WHERE tenant_id = ? AND jour = ?
				""", Integer.class, tenantId, jour);
		return appels == null ? 0 : appels;
	}

	/** Les compteurs d'hier ne servent plus à rien. */
	public int purger(UUID tenantId, LocalDate jour) {
		return jdbc.update("DELETE FROM budget_hlr WHERE tenant_id = ? AND jour < ?",
				tenantId, jour);
	}
}
