package ai.attenso.facturation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Abonnements et factures d'une clinique. */
@Repository
public class FacturationDao {

	private final JdbcTemplate jdbc;

	public FacturationDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// --- Abonnement -----------------------------------------------------

	public Optional<Palier> palierEnCours(UUID tenantId) {
		return jdbc.queryForList("SELECT palier FROM abonnement WHERE tenant_id = ? "
				+ "AND fin IS NULL", String.class, tenantId)
				.stream().findFirst().map(Palier::valueOf);
	}

	/**
	 * Le palier applicable à une période passée.
	 *
	 * <p>Et non le palier courant : un client conserve son prix sur son
	 * palier, donc refacturer janvier au tarif de mars serait faux. C'est la
	 * première chose qu'un client vérifie quand il conteste.
	 */
	public Optional<Palier> palierAu(UUID tenantId, LocalDate jour) {
		return jdbc.queryForList("""
				SELECT palier FROM abonnement
				WHERE tenant_id = ? AND debut <= ? AND (fin IS NULL OR fin >= ?)
				ORDER BY debut DESC LIMIT 1
				""", String.class, tenantId, jour, jour)
				.stream().findFirst().map(Palier::valueOf);
	}

	/** Ferme l'abonnement en cours s'il existe, puis en ouvre un neuf. */
	public void souscrire(UUID tenantId, Palier palier, LocalDate debut) {
		jdbc.update("UPDATE abonnement SET fin = ? WHERE tenant_id = ? AND fin IS NULL",
				debut.minusDays(1), tenantId);
		jdbc.update("INSERT INTO abonnement (tenant_id, palier, debut) VALUES (?, ?, ?)",
				tenantId, palier.name(), debut);
	}

	// --- Factures -------------------------------------------------------

	/**
	 * @return {@code false} si la période était déjà facturée. L'unicité est en
	 *         base : la tâche mensuelle peut repasser sans facturer deux fois,
	 *         et c'est PostgreSQL qui le garantit, pas un {@code if}.
	 */
	public boolean emettre(Facture facture, UUID tenantId) {
		return jdbc.update("""
				INSERT INTO facture (id, tenant_id, numero, periode, palier, visites,
				                     visites_incluses, abonnement_centimes,
				                     depassement_centimes, total_centimes, cout_sms_centimes)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (tenant_id, periode) DO NOTHING
				""", facture.id(), tenantId, facture.numero(), facture.periode(),
				facture.palier().name(), facture.visites(), facture.visitesIncluses(),
				facture.abonnementCentimes(), facture.depassementCentimes(),
				facture.totalCentimes(), facture.coutSmsCentimes()) > 0;
	}

	public List<Facture> factures(UUID tenantId) {
		return jdbc.query("SELECT * FROM facture WHERE tenant_id = ? ORDER BY periode DESC",
				FacturationDao::lire, tenantId);
	}

	public Optional<Facture> facture(UUID tenantId, UUID id) {
		return jdbc.query("SELECT * FROM facture WHERE tenant_id = ? AND id = ?",
				FacturationDao::lire, tenantId, id).stream().findFirst();
	}

	// --- Mesure de la période -------------------------------------------

	/**
	 * Les visites d'un mois : tous les tickets, quelle que soit leur origine.
	 *
	 * <p>Un ticket saisi par l'accueil pour un client sans smartphone est une
	 * visite comme une autre : il consomme la file et déclenche les mêmes SMS.
	 * Les tickets annulés comptent aussi — un SMS de ticket est déjà parti
	 * quand l'annulation arrive.
	 */
	public int visitesDuMois(UUID tenantId, LocalDate premierJour) {
		Integer n = jdbc.queryForObject("""
				SELECT count(*)::int FROM ticket
				WHERE tenant_id = ? AND jour >= ? AND jour < ?
				""", Integer.class, tenantId, premierJour, premierJour.plusMonths(1));
		return n == null ? 0 : n;
	}

	/** Ce que les SMS de la période ont réellement coûté, pour la marge. */
	public BigDecimal coutSmsDuMois(UUID tenantId, LocalDate premierJour) {
		BigDecimal cout = jdbc.queryForObject("""
				SELECT COALESCE(sum(n.cout_centimes), 0) FROM notification n
				JOIN ticket t ON t.id = n.ticket_id
				WHERE n.tenant_id = ? AND t.jour >= ? AND t.jour < ?
				  AND n.statut = 'ENVOYE'
				""", BigDecimal.class, tenantId, premierJour, premierJour.plusMonths(1));
		return cout == null ? BigDecimal.ZERO : cout;
	}

	private static Facture lire(ResultSet l, int i) throws SQLException {
		return new Facture(l.getObject("id", UUID.class), l.getString("numero"),
				l.getDate("periode").toLocalDate(), Palier.valueOf(l.getString("palier")),
				l.getInt("visites"), l.getInt("visites_incluses"),
				l.getInt("abonnement_centimes"), l.getInt("depassement_centimes"),
				l.getInt("total_centimes"), l.getBigDecimal("cout_sms_centimes"),
				l.getTimestamp("emise_le").toInstant());
	}
}
