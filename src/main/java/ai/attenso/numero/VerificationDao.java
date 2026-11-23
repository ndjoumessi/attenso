package ai.attenso.numero;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cache et comptage des vérifications HLR.
 *
 * <p>Les deux rôles sont indissociables : le cache est ce qui rend le budget
 * tenable, et le budget est ce qui rend le cache nécessaire. Un numéro refusé
 * ne crée pas de ticket, donc rien d'autre dans le système ne garde trace de la
 * dépense qu'il a causée.
 */
@Repository
public class VerificationDao {

	private final JdbcTemplate jdbc;

	public VerificationDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<ResultatVerification> retrouver(UUID tenantId, LocalDate jour,
			String telephoneE164) {
		List<ResultatVerification> trouves = jdbc.query("""
				SELECT verdict, detail FROM verification_numero
				WHERE tenant_id = ? AND jour = ? AND telephone_e164 = ?
				""",
				(ligne, i) -> new ResultatVerification(
						Verdict.valueOf(ligne.getString("verdict")), ligne.getString("detail")),
				tenantId, jour, telephoneE164);
		return trouves.stream().findFirst();
	}

	/**
	 * Enregistre le verdict. {@code ON CONFLICT DO NOTHING} : deux demandes
	 * simultanées pour le même numéro ne créent qu'une ligne, donc ne comptent
	 * qu'une dépense.
	 */
	public void enregistrer(UUID tenantId, LocalDate jour, String telephoneE164,
			ResultatVerification resultat) {
		jdbc.update("""
				INSERT INTO verification_numero
				  (tenant_id, jour, telephone_e164, verdict, detail)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (tenant_id, jour, telephone_e164) DO NOTHING
				""", tenantId, jour, telephoneE164, resultat.verdict().name(),
				resultat.detail());
	}

	/**
	 * Nombre de verdicts en cache pour la journée. Ce n'est PAS le budget
	 * consommé — celui-là est tenu par {@link BudgetHlrDao}, parce qu'un
	 * compteur de lignes ne peut pas se réserver de façon indivisible.
	 */
	public long compterDuJour(UUID tenantId, LocalDate jour) {
		Long total = jdbc.queryForObject("""
				SELECT count(*) FROM verification_numero WHERE tenant_id = ? AND jour = ?
				""", Long.class, tenantId, jour);
		return total == null ? 0 : total;
	}

	/**
	 * Purge RGPD : cette table porte des numéros de téléphone, exactement comme
	 * {@code ticket}. Elle suit la même règle — effacée à J+1. Rien n'en est
	 * conservé, puisque son seul rôle est journalier.
	 */
	public int purger(UUID tenantId, LocalDate jour) {
		return jdbc.update("DELETE FROM verification_numero WHERE tenant_id = ? AND jour < ?",
				tenantId, jour);
	}
}
